package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.constants.StatisticsPeriod;
import com.github.svenfran.budgetapp.budgetappbackend.dto.StatisticsCategoryDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.StatisticsDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.StatisticsTimelineEntryDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.GroupNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.InvalidStatisticsRequestException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOrMemberOfGroupException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.helper.StatisticsCalculator;
import com.github.svenfran.budgetapp.budgetappbackend.helper.Translator;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.stream.Collectors;

import static com.github.svenfran.budgetapp.budgetappbackend.helper.CostShareCalculator.toEuro;

/**
 * Statistikseite (DIV-63): Ausgaben, Zeitreihe und Kategorien einer Gruppe oder aller Gruppen des Nutzers, dazu der
 * aktuelle offene Saldo wie in der Übersicht. Rein lesend.
 */
@Service
public class StatisticsService {

    private static final String SETTLEMENT_CATEGORY_KEY = "categories.default.settlement";
    private static final List<Locale> SETTLEMENT_LOCALES =
            List.of(Locale.GERMAN, Locale.ENGLISH, Locale.forLanguageTag("es"));

    private final DataLoaderService dataLoaderService;
    private final VerificationService verificationService;
    private final GroupService groupService;
    private final SpendingsOverviewService spendingsOverviewService;
    private final CartRepository cartRepository;
    private final Translator translator;

    public StatisticsService(DataLoaderService dataLoaderService, VerificationService verificationService,
                             GroupService groupService, SpendingsOverviewService spendingsOverviewService,
                             CartRepository cartRepository, Translator translator) {
        this.dataLoaderService = dataLoaderService;
        this.verificationService = verificationService;
        this.groupService = groupService;
        this.spendingsOverviewService = spendingsOverviewService;
        this.cartRepository = cartRepository;
        this.translator = translator;
    }

    /**
     * Ein Snapshot für Berechtigung, Carts, Jahre und Saldo: Ausgaben und Saldo einer Antwort passen auch bei parallel
     * committeten Änderungen zusammen.
     *
     * @param groupId ohne Angabe alle Gruppen, in denen der Nutzer Owner oder Mitglied ist
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StatisticsDto getStatistics(Long groupId, StatisticsPeriod period, Integer year, Integer month)
            throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException,
            InvalidStatisticsRequestException {
        validate(period, year, month);
        var user = dataLoaderService.getAuthenticatedUser();
        var groups = loadGroups(user, groupId);

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
                availableYears, settlementNames());

        var dto = new StatisticsDto();
        dto.setGroupId(groupId);
        dto.setPeriod(period);
        dto.setYear(effectiveYear);
        dto.setMonth(effectiveMonth);
        dto.setTotalSpent(toEuro(result.totalCents()));
        dto.setPaidByUser(toEuro(result.paidCents()));
        dto.setCurrentBalance(toEuro(balanceCents));
        dto.setAvailableYears(List.copyOf(availableYears));
        dto.setTimeline(result.timeline().stream()
                .map(entry -> new StatisticsTimelineEntryDto(entry.year(), entry.month(), entry.week(),
                        entry.from(), entry.to(), toEuro(entry.amountCents())))
                .toList());
        dto.setCategories(result.categories().stream()
                .map(category -> new StatisticsCategoryDto(category.name(), toEuro(category.amountCents()),
                        category.settlement()))
                .toList());
        return dto;
    }

    private void validate(StatisticsPeriod period, Integer year, Integer month) throws InvalidStatisticsRequestException {
        if (period == StatisticsPeriod.ALL) {
            return;
        }
        if (year == null) {
            throw new InvalidStatisticsRequestException(String.format("Year is required for period %s", period));
        }
        try {
            ChronoField.YEAR.checkValidIntValue(year);
        } catch (DateTimeException e) {
            throw new InvalidStatisticsRequestException(String.format("Year %s is not valid", year));
        }
        if (period == StatisticsPeriod.MONTH && (month == null || month < 1 || month > 12)) {
            throw new InvalidStatisticsRequestException("Month must be between 1 and 12 for period MONTH");
        }
    }

    /** Mit groupId nur diese Gruppe (Berechtigung geprüft), sonst alle Gruppen des Nutzers, jede genau einmal. */
    private List<Group> loadGroups(User user, Long groupId)
            throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        if (groupId != null) {
            var group = dataLoaderService.loadGroup(groupId);
            verificationService.verifyIsPartOfGroup(user, group);
            return List.of(group);
        }
        // getGroupsByMemberOrOwner liefert eine Gruppe doppelt, wenn der Owner zugleich in members steht
        return groupService.getGroupsByMemberOrOwner()
                .collect(Collectors.toMap(Group::getId, group -> group, (first, duplicate) -> first, TreeMap::new))
                .values().stream().toList();
    }

    private Set<String> settlementNames() {
        return SETTLEMENT_LOCALES.stream()
                .map(locale -> StatisticsCalculator.normalize(translator.translate(SETTLEMENT_CATEGORY_KEY, null, locale)))
                .collect(Collectors.toSet());
    }
}
