package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.constants.UserEnum;
import com.github.svenfran.budgetapp.budgetappbackend.dto.*;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart;
import com.github.svenfran.budgetapp.budgetappbackend.entity.GroupMembershipHistory;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.GroupNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOrMemberOfGroupException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

import static com.github.svenfran.budgetapp.budgetappbackend.helper.CostShareCalculator.commonDenominator;
import static com.github.svenfran.budgetapp.budgetappbackend.helper.CostShareCalculator.roundBalances;
import static com.github.svenfran.budgetapp.budgetappbackend.helper.CostShareCalculator.shareNumerator;
import static com.github.svenfran.budgetapp.budgetappbackend.helper.CostShareCalculator.toCents;
import static com.github.svenfran.budgetapp.budgetappbackend.helper.CostShareCalculator.toEuro;
import static com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils.NO_END_DATE;
import static com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils.toLocalDate;

@Service
public class SpendingsOverviewService {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private DataLoaderService dataLoaderService;

    @Autowired
    private VerificationService verificationService;


    // Overview for all Years
    public SpendingsOverviewDto getSpendingsForGroupAndAllYears(Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(groupId);
        verificationService.verifyIsPartOfGroup(user, group);
        var availableYears = cartRepository.getAvailableYearsForGroup(groupId);

        var spendingsOverview = new SpendingsOverviewDto();
        spendingsOverview.setGroupId(groupId);
        spendingsOverview.setSpendingsTotalYear(getSpendingsOverviewTotalAllYears(groupId));
        spendingsOverview.setSpendingsPerYear(getSpendingsOverviewPerYear(groupId, availableYears));
        spendingsOverview.setAvailableYears(availableYears);
        return spendingsOverview;
    }

    private SpendingsOverviewTotalYearDto getSpendingsOverviewTotalAllYears(Long groupId) throws UserNotFoundException {
        var membership = dataLoaderService.loadMembershipHistoryForGroup(groupId);
        var carts = dataLoaderService.loadCartListForGroup(groupId);
        var userIds = membership.stream().map(GroupMembershipHistory::getUserId).distinct().toList();
        var shares = calculateShares(carts, membership);
        var spendingsOverviewTotalYearDto = new SpendingsOverviewTotalYearDto();
        var userTotals = new ArrayList<UserTotals>();

        long totalCents = 0;

        if (hasCartsForGroup(carts, groupId)) {
            for (Long userId : userIds) {
                long sumCents = 0;
                var shareNumerator = BigInteger.ZERO;

                for (Cart cart : carts) {
                    if (cart.getUser().getId().equals(userId)) {
                        sumCents += toCents(cart.getAmount());
                    }

                    shareNumerator = shareNumerator.add(shares.numerator(cart, userId));
                }

                var diffNumerator = shares.toNumerator(sumCents).subtract(shareNumerator);
                totalCents += sumCents;
                var userName = handleUserName(userId, groupId);
                userTotals.add(new UserTotals(userId, userName, sumCents, diffNumerator));
            }
        }

        spendingsOverviewTotalYearDto.setSumTotalYear(toEuro(totalCents));
        spendingsOverviewTotalYearDto.setSpendingsTotalUser(toUserDtos(userTotals, shares));

        return spendingsOverviewTotalYearDto;
    }

    private List<SpendingsOverviewPerYearDto> getSpendingsOverviewPerYear(Long groupId, List<Integer> availableYears) throws UserNotFoundException {
        var membership = dataLoaderService.loadMembershipHistoryForGroup(groupId);
        var carts = dataLoaderService.loadCartListForGroup(groupId);
        var userIds = membership.stream().map(GroupMembershipHistory::getUserId).distinct().toList();
        var shares = calculateShares(carts, membership);
        var spendingsOverviewPerYearDtoList = new ArrayList<SpendingsOverviewPerYearDto>();

        for (Integer year : availableYears) {
            var spendingsOverviewPerYearDto = new SpendingsOverviewPerYearDto();
            var userTotals = new ArrayList<UserTotals>();

            spendingsOverviewPerYearDto.setSumTotalYear(getTotalAmountForYear(carts, year));
            spendingsOverviewPerYearDto.setYear(year);

            for (Long userId : userIds) {
                long sumCents = 0;
                var shareNumerator = BigInteger.ZERO;

                var validMemberships = membership.stream()
                        .filter(gmh -> gmh.getUserId().equals(userId))
                        .toList();

                for (Cart cart : carts) {
                    if (toLocalDate(cart.getDatePurchased()).getYear() == year) {
                        if (cart.getUser().getId().equals(userId)) {
                            sumCents += toCents(cart.getAmount());
                        }

                        shareNumerator = shareNumerator.add(shares.numerator(cart, userId));
                    }
                }

                var diffNumerator = shares.toNumerator(sumCents).subtract(shareNumerator);
                var userName = handleUserName(userId, groupId);
                if (wasUserMemberInYear(validMemberships, year)) {
                    userTotals.add(new UserTotals(userId, userName, sumCents, diffNumerator));
                }
            }
            spendingsOverviewPerYearDto.setSpendingsYearlyUser(toUserDtos(userTotals, shares));
            spendingsOverviewPerYearDtoList.add(spendingsOverviewPerYearDto);
        }

        return spendingsOverviewPerYearDtoList;
    }

    // Overview for one Year
    public SpendingsOverviewDto getSpendingsForGroupAndYear(int year, Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(groupId);
        verificationService.verifyIsPartOfGroup(user, group);
        var availableYears = cartRepository.getAvailableYearsForGroup(groupId);

        var spendingsOverview = new SpendingsOverviewDto();
        spendingsOverview.setGroupId(groupId);
        spendingsOverview.setYear(year);
        spendingsOverview.setSpendingsTotalYear(getSpendingsOverviewTotalYear(year, groupId));
        spendingsOverview.setSpendingsPerMonth(getSpendingsOverviewPerMonth(year, groupId));
        spendingsOverview.setAvailableYears(availableYears);
        return spendingsOverview;
    }

    private SpendingsOverviewTotalYearDto getSpendingsOverviewTotalYear(int year, Long groupId) throws UserNotFoundException {
        var membership = dataLoaderService.loadMembershipHistoryForGroup(groupId);
        var carts = dataLoaderService.loadCartListForGroup(groupId);
        var userIds = membership.stream().map(GroupMembershipHistory::getUserId).distinct().toList();
        var shares = calculateShares(carts, membership);
        var spendingsOverviewTotalYearDto = new SpendingsOverviewTotalYearDto();
        var userTotals = new ArrayList<UserTotals>();

        spendingsOverviewTotalYearDto.setSumTotalYear(getTotalAmountForYear(carts, year));

        if (hasCartsForYear(carts, year)) {
            for (Long userId : userIds) {
                long sumCents = 0;
                var shareNumerator = BigInteger.ZERO;

                var validMemberships = membership.stream()
                        .filter(gmh -> gmh.getUserId().equals(userId))
                        .toList();

                for (Cart cart : carts) {
                    if (toLocalDate(cart.getDatePurchased()).getYear() == year) {
                        if (cart.getUser().getId().equals(userId)) {
                            sumCents += toCents(cart.getAmount());
                        }

                        shareNumerator = shareNumerator.add(shares.numerator(cart, userId));
                    }
                }

                var diffNumerator = shares.toNumerator(sumCents).subtract(shareNumerator);
                var userName = handleUserName(userId, groupId);
                if (wasUserMemberInYear(validMemberships, year)) {
                    userTotals.add(new UserTotals(userId, userName, sumCents, diffNumerator));
                }
            }
        }

        spendingsOverviewTotalYearDto.setSpendingsTotalUser(toUserDtos(userTotals, shares));

        return spendingsOverviewTotalYearDto;
    }

    private List<SpendingsOverviewPerMonthDto> getSpendingsOverviewPerMonth(int year, Long groupId) throws UserNotFoundException {
        var membership = dataLoaderService.loadMembershipHistoryForGroup(groupId);
        var carts = dataLoaderService.loadCartListForGroup(groupId);
        var userIds = membership.stream().map(GroupMembershipHistory::getUserId).distinct().toList();
        var shares = calculateShares(carts, membership);
        var spendingsOverviewPerMonthDtoList = new ArrayList<SpendingsOverviewPerMonthDto>();
        var availableMonth = getAvailableMonthsForYear(carts, year);

        for (Integer month : availableMonth) {
            var spendingsOverviewPerMonthDto = new SpendingsOverviewPerMonthDto();
            var userTotals = new ArrayList<UserTotals>();

            spendingsOverviewPerMonthDto.setMonth(month);
            spendingsOverviewPerMonthDto.setMonthName(getMonthName(month));
            spendingsOverviewPerMonthDto.setSumTotalMonth(getTotalAmountForMonth(carts, year, month));

            for (Long userId : userIds) {
                long sumCents = 0;
                var shareNumerator = BigInteger.ZERO;

                var validMemberships = membership.stream()
                        .filter(gmh -> gmh.getUserId().equals(userId))
                        .toList();

                for (Cart cart : carts) {
                    if (toLocalDate(cart.getDatePurchased()).getMonthValue() == month
                        && toLocalDate(cart.getDatePurchased()).getYear() == year) {
                        if (cart.getUser().getId().equals(userId)) {
                            sumCents += toCents(cart.getAmount());
                        }

                        shareNumerator = shareNumerator.add(shares.numerator(cart, userId));
                    }
                }

                var diffNumerator = shares.toNumerator(sumCents).subtract(shareNumerator);
                var userName = handleUserName(userId, groupId);
                if (wasUserMemberInMonth(validMemberships, year, month)) {
                    userTotals.add(new UserTotals(userId, userName, sumCents, diffNumerator));
                }
            }
            spendingsOverviewPerMonthDto.setSpendingsMonthlyUser(toUserDtos(userTotals, shares));
            spendingsOverviewPerMonthDtoList.add(spendingsOverviewPerMonthDto);
        }

        return spendingsOverviewPerMonthDtoList;
    }

    // Helper Methods

    /**
     * Beteiligte je Cart (DIV-38): alle, die am Kaufdatum laut Historie Mitglied waren. Anteile werden exakt als
     * Bruch zum gemeinsamen Nenner geführt und erst als Saldo gerundet (toUserDtos). averagePerMember wird bewusst
     * nicht verwendet.
     */
    private Shares calculateShares(List<Cart> carts, List<GroupMembershipHistory> membership) {
        var participantsByCartId = new HashMap<Long, Set<Long>>();
        for (Cart cart : carts) {
            var participantIds = membership.stream()
                    .filter(gmh -> hasValidMembership(cart.getDatePurchased(), gmh))
                    .map(GroupMembershipHistory::getUserId)
                    .collect(Collectors.toSet());
            participantsByCartId.put(cart.getId(), participantIds);
        }
        var denominator = commonDenominator(participantsByCartId.values().stream().map(Set::size).toList());
        return new Shares(participantsByCartId, denominator);
    }

    /** Rundet die exakten Salden einer Periode gemeinsam, sodass sie zusammen exakt 0 ergeben. */
    private List<SpendingsOverviewUserDto> toUserDtos(List<UserTotals> userTotals, Shares shares) {
        var exactDiffs = new LinkedHashMap<Long, BigInteger>();
        userTotals.forEach(totals -> exactDiffs.put(totals.userId(), totals.diffNumerator()));
        var diffs = roundBalances(exactDiffs, shares.denominator());

        var userDtos = new ArrayList<SpendingsOverviewUserDto>();
        for (UserTotals totals : userTotals) {
            userDtos.add(new SpendingsOverviewUserDto(totals.userId(), totals.userName(),
                    toEuro(totals.sumCents()), toEuro(diffs.get(totals.userId()))));
        }
        return userDtos;
    }

    private record Shares(Map<Long, Set<Long>> participantsByCartId, BigInteger denominator) {

        /** Exakter Anteil des Nutzers am Cart als Zähler zum Nenner; 0, wenn er nicht beteiligt war. */
        BigInteger numerator(Cart cart, Long userId) {
            var participants = participantsByCartId.get(cart.getId());
            return participants.contains(userId)
                    ? shareNumerator(toCents(cart.getAmount()), participants.size(), denominator)
                    : BigInteger.ZERO;
        }

        BigInteger toNumerator(long cents) {
            return BigInteger.valueOf(cents).multiply(denominator);
        }
    }

    private record UserTotals(Long userId, String userName, long sumCents, BigInteger diffNumerator) {
    }

    private boolean hasValidMembership(Date datePurchased, GroupMembershipHistory gmh) {
        if (datePurchased == null || gmh == null || gmh.getMembershipStart() == null) {
            return false;
        }
        // Zeitkomponente entfernen
        LocalDate purchasedDate = toLocalDate(datePurchased);
        LocalDate startDate = toLocalDate(gmh.getMembershipStart());
        LocalDate endDate = (gmh.getMembershipEnd() != null)
                ? toLocalDate(gmh.getMembershipEnd())
                : NO_END_DATE;

        return !purchasedDate.isBefore(startDate) && !purchasedDate.isAfter(endDate);
    }

    public double getTotalAmountForYear(List<Cart> carts, int year) {
        return toEuro(carts.stream()
                .filter(cart -> {
                    LocalDate date = toLocalDate(cart.getDatePurchased());
                    return date.getYear() == year;
                })
                .mapToLong(cart -> toCents(cart.getAmount()))
                .sum());
    }

    public List<Integer> getAvailableMonthsForYear(List<Cart> carts, int year) {
        return carts.stream()
                .map(cart -> toLocalDate(cart.getDatePurchased())) // Datum umwandeln
                .filter(date -> date.getYear() == year)
                .map(LocalDate::getMonthValue)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    public String getMonthName(Integer monthNumber) {
        return Month.of(monthNumber).getDisplayName(TextStyle.FULL, Locale.GERMAN);
    }

    public double getTotalAmountForMonth(List<Cart> carts, int year, int month) {
        return toEuro(carts.stream()
                .filter(cart -> {
                    LocalDate date = toLocalDate(cart.getDatePurchased());
                    return date.getYear() == year && date.getMonthValue() == month;
                })
                .mapToLong(cart -> toCents(cart.getAmount()))
                .sum());
    }

    public boolean hasCartsForYear(List<Cart> carts, int year) {
        return carts.stream()
                .map(cart -> toLocalDate(cart.getDatePurchased()))
                .anyMatch(date -> date.getYear() == year);
    }

    public boolean hasCartsForGroup(List<Cart> carts, Long groupId) {
        return carts.stream()
                .anyMatch(cart -> Objects.equals(cart.getGroup().getId(), groupId));
    }

    public boolean wasUserMemberInYear(List<GroupMembershipHistory> membershipHistory, int year) {
        return membershipHistory.stream()
                .anyMatch(gmh -> {
                    LocalDate start = toLocalDate(gmh.getMembershipStart());
                    LocalDate end = (gmh.getMembershipEnd() != null)
                            ? toLocalDate(gmh.getMembershipEnd())
                            : NO_END_DATE; // Falls `membership_end` null ist

                    // Prüfen, ob das Jahr innerhalb des Mitgliedschaftszeitraums liegt
                    return (start.getYear() <= year && end.getYear() >= year);
                });
    }

    public boolean wasUserMemberInMonth(List<GroupMembershipHistory> membershipHistory, int year, int month) {
        return membershipHistory.stream()
                .anyMatch(gmh -> {
                    LocalDate start = toLocalDate(gmh.getMembershipStart());
                    LocalDate end = (gmh.getMembershipEnd() != null)
                            ? toLocalDate(gmh.getMembershipEnd())
                            : NO_END_DATE; // Falls `membership_end` null ist

                    // Prüfen, ob der Monat innerhalb des Mitgliedschaftszeitraums liegt
                    LocalDate firstOfMonth = LocalDate.of(year, month, 1);
                    LocalDate lastOfMonth = firstOfMonth.withDayOfMonth(firstOfMonth.lengthOfMonth());

                    return (!start.isAfter(lastOfMonth) && !end.isBefore(firstOfMonth));
                });
    }

    private boolean userIsCurrentlyMember(Long userId, Long groupId) throws UserNotFoundException {
        var user = dataLoaderService.loadUser(userId);
        if (user.getName().equals(UserEnum.USER_DELETED.getName())) {
            return true;
        }

        return dataLoaderService.loadMembershipHistoryForGroupAndUser(groupId, userId)
                .stream()
                .anyMatch(gmh -> gmh.getMembershipEnd() == null);
    }

    private String handleUserName(Long userId, Long groupId) throws UserNotFoundException {
        var userName = dataLoaderService.loadUser(userId).getName();

        if (userIsCurrentlyMember(userId, groupId)) {
            return userName;
        } else {
            return String.format("%s %s",userName, UserEnum.USER_REMOVED.getName());
        }
    }
}
