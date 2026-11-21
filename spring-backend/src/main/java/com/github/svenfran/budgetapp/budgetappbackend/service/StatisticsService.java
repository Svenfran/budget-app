package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.constants.StatisticsPeriod;
import com.github.svenfran.budgetapp.budgetappbackend.constants.SupportedCurrency;
import com.github.svenfran.budgetapp.budgetappbackend.dto.StatisticsCategoryDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.StatisticsDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.StatisticsGroupViewDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.StatisticsTimelineEntryDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.GroupNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.InvalidStatisticsRequestException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOrMemberOfGroupException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UnsupportedCurrencyException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.helper.StatisticsCalculator;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

import static com.github.svenfran.budgetapp.budgetappbackend.helper.CostShareCalculator.toEuro;

/**
 * Statistikseite (DIV-63): Ausgaben, Zeitreihe und Kategorien einer Gruppe oder aller Gruppen des Nutzers, dazu der
 * aktuelle offene Saldo wie in der Übersicht. Rein lesend.
 * <p>
 * Jede Antwort enthält beide Perspektiven (DIV-72): die persönlichen Felder und {@code groupView} mit allen Carts der
 * gewählten Gruppen sowie der Anzahl ihrer aktuellen Personen. Der Perspektivwechsel im Frontend braucht keinen Request.
 * <p>
 * Währungen (DIV-77): Beträge verschiedener Währungen werden nie addiert. Ohne {@code groupId} zählen nur die Gruppen der
 * gewählten Währung ({@code currency}, ohne Angabe EUR – alte App-Versionen); mit {@code groupId} gilt deren Währung.
 * {@code availableCurrencies} nennt unabhängig vom Filter alle Währungen der Gruppen des Nutzers (für die Auswahl).
 */
@Service
public class StatisticsService {

    private final DataLoaderService dataLoaderService;
    private final VerificationService verificationService;
    private final GroupService groupService;
    private final SpendingsOverviewService spendingsOverviewService;
    private final CartRepository cartRepository;

    public StatisticsService(DataLoaderService dataLoaderService, VerificationService verificationService,
                             GroupService groupService, SpendingsOverviewService spendingsOverviewService,
                             CartRepository cartRepository) {
        this.dataLoaderService = dataLoaderService;
        this.verificationService = verificationService;
        this.groupService = groupService;
        this.spendingsOverviewService = spendingsOverviewService;
        this.cartRepository = cartRepository;
    }

    /**
     * Ein Snapshot für Berechtigung, Carts, Jahre und Saldo: Ausgaben und Saldo einer Antwort passen auch bei parallel
     * committeten Änderungen zusammen.
     *
     * @param groupId  ohne Angabe alle Gruppen, in denen der Nutzer Owner oder Mitglied ist
     * @param currency nur ohne groupId wirksam: Währung der zusammengefassten Gruppen, ohne Angabe EUR; ein nicht
     *                 unterstützter Code wird immer abgelehnt (400)
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StatisticsDto getStatistics(Long groupId, String currency, StatisticsPeriod period, Integer year, Integer month)
            throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException,
            InvalidStatisticsRequestException, UnsupportedCurrencyException {
        validate(period, year, month);
        var requestedCurrency = SupportedCurrency.fromCode(currency);
        var user = dataLoaderService.getAuthenticatedUser();
        var allGroups = groupService.getDistinctGroupsByMemberOrOwner();
        var selectedCurrency = groupId != null ? null
                : (requestedCurrency != null ? requestedCurrency : SupportedCurrency.DEFAULT);
        var groups = loadGroups(user, groupId, allGroups, selectedCurrency);
        var currencyCode = groupId != null ? groups.get(0).getCurrencyCode() : selectedCurrency;

        var carts = new ArrayList<Cart>();
        var availableYears = new TreeSet<Integer>(Comparator.reverseOrder());
        long balanceCents = 0;
        for (var group : groups) {
            carts.addAll(dataLoaderService.loadCartListForGroup(group.getId()));
            availableYears.addAll(cartRepository.getAvailableYearsForGroup(group.getId()));
            balanceCents += spendingsOverviewService.currentBalanceCents(group.getId(), user.getId());
        }

        var effectiveYear = period == StatisticsPeriod.ALL ? null : year;
        var effectiveMonth = period == StatisticsPeriod.MONTH ? month : null;
        var result = StatisticsCalculator.calculate(carts, user.getId(), period, effectiveYear, effectiveMonth,
                availableYears);

        var dto = new StatisticsDto();
        dto.setGroupId(groupId);
        dto.setCurrencyCode(currencyCode.name());
        dto.setAvailableCurrencies(availableCurrencies(allGroups));
        dto.setPeriod(period);
        dto.setYear(effectiveYear);
        dto.setMonth(effectiveMonth);
        dto.setTotalSpent(toEuro(result.totalCents()));
        dto.setPaidByUser(toEuro(result.paidCents()));
        dto.setPaidCartCount(result.paidCartCount());
        dto.setCurrentBalance(toEuro(balanceCents));
        dto.setAvailableYears(List.copyOf(availableYears));
        dto.setTimeline(toTimelineDtos(result.timeline()));
        dto.setCategories(toCategoryDtos(result.categories()));
        var group = result.group();
        dto.setGroupView(new StatisticsGroupViewDto(group.cartCount(), toEuro(group.averageCents()), memberCount(groups),
                toTimelineDtos(group.timeline()), toCategoryDtos(group.categories())));
        return dto;
    }

    /** Aktuelle Personen der Gruppen (Owner und Mitglieder), jede genau einmal – auch wenn der Owner in members steht. */
    private int memberCount(List<Group> groups) {
        return (int) groups.stream()
                .flatMap(group -> Stream.concat(Stream.of(group.getOwner()), group.getMembers().stream()))
                .map(User::getId)
                .distinct()
                .count();
    }

    private List<StatisticsTimelineEntryDto> toTimelineDtos(List<StatisticsCalculator.TimelineEntry> entries) {
        return entries.stream()
                .map(entry -> new StatisticsTimelineEntryDto(entry.year(), entry.month(), entry.week(),
                        entry.from(), entry.to(), toEuro(entry.amountCents())))
                .toList();
    }

    private List<StatisticsCategoryDto> toCategoryDtos(List<StatisticsCalculator.CategoryEntry> categories) {
        return categories.stream()
                .map(category -> new StatisticsCategoryDto(category.name(), toEuro(category.amountCents()),
                        category.settlement()))
                .toList();
    }

    private void validate(StatisticsPeriod period, Integer year, Integer month) throws InvalidStatisticsRequestException {
        if (period == StatisticsPeriod.ALL) {
            return;
        }
        if (year == null) {
            throw new InvalidStatisticsRequestException(String.format("Year is required for period %s", period));
        }
        // Technische, keine fachliche Grenze: Am Rand des LocalDate-Bereichs reichen die Kalenderwochen über
        // LocalDate.MIN/MAX hinaus (Codex-Review F-001), daher sind die beiden Randjahre ausgeschlossen.
        if (year <= LocalDate.MIN.getYear() || year >= LocalDate.MAX.getYear()) {
            throw new InvalidStatisticsRequestException(String.format("Year %s is not valid", year));
        }
        if (period == StatisticsPeriod.MONTH && (month == null || month < 1 || month > 12)) {
            throw new InvalidStatisticsRequestException("Month must be between 1 and 12 for period MONTH");
        }
    }

    /**
     * Mit groupId nur diese Gruppe (Berechtigung geprüft), sonst die Gruppen des Nutzers in der gewählten Währung, jede
     * genau einmal. Keine passende Gruppe ergibt leere Zahlen, keinen Fehler.
     */
    private List<Group> loadGroups(User user, Long groupId, List<Group> allGroups, SupportedCurrency currency)
            throws GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        if (groupId != null) {
            var group = dataLoaderService.loadGroup(groupId);
            verificationService.verifyIsPartOfGroup(user, group);
            return List.of(group);
        }
        return allGroups.stream().filter(group -> group.getCurrencyCode() == currency).toList();
    }

    /** Verschiedene Währungen aller Gruppen des Nutzers, in der Reihenfolge von SupportedCurrency. */
    private static List<String> availableCurrencies(List<Group> groups) {
        var codes = EnumSet.noneOf(SupportedCurrency.class);
        groups.forEach(group -> codes.add(group.getCurrencyCode()));
        return codes.stream().map(Enum::name).toList();
    }
}
