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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;

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
        var spendingsOverviewTotalYearDto = new SpendingsOverviewTotalYearDto();
        var spendingsTotalUserList = new ArrayList<SpendingsOverviewUserDto>();

        var totalSum = 0.0;

        if (hasCartsForGroup(carts, groupId)) {
            for (Long userId : userIds) {
                double sum = 0.0;
                double sumAveragePerMember = 0.0;

                var validMemberships = membership.stream()
                        .filter(gmh -> gmh.getUserId().equals(userId))
                        .toList();

                for (Cart cart : carts) {
                    if (cart.getUser().getId().equals(userId)) {
                        sum += cart.getAmount();
                    }

                    for (GroupMembershipHistory gmh : validMemberships) {
                        if (gmh.getUserId().equals(userId) && hasValidMembership(cart.getDatePurchased(), gmh)) {
                            sumAveragePerMember += cart.getAveragePerMember();
                            break; // Sobald eine gültige Mitgliedschaft gefunden wurde, weiter zum nächsten Cart
                        }
                    }
                }

                double diff = sum - sumAveragePerMember;
                totalSum += sum;
                var userName = dataLoaderService.loadUser(userId).getName();
                userName = userIsCurrentlyMember(userId, groupId) ? userName : UserEnum.USER_REMOVED.getName();
                spendingsTotalUserList.add(new SpendingsOverviewUserDto(userId, userName, roundValue(sum), roundValue(diff)));
            }
        }

        spendingsOverviewTotalYearDto.setSumTotalYear(totalSum);
        spendingsOverviewTotalYearDto.setSpendingsTotalUser(spendingsTotalUserList);

        return spendingsOverviewTotalYearDto;
    }

    private List<SpendingsOverviewPerYearDto> getSpendingsOverviewPerYear(Long groupId, List<Integer> availableYears) throws UserNotFoundException {
        var membership = dataLoaderService.loadMembershipHistoryForGroup(groupId);
        var carts = dataLoaderService.loadCartListForGroup(groupId);
        var userIds = membership.stream().map(GroupMembershipHistory::getUserId).distinct().toList();
        var spendingsOverviewPerYearDtoList = new ArrayList<SpendingsOverviewPerYearDto>();

        for (Integer year : availableYears) {
            var spendingsOverviewPerYearDto = new SpendingsOverviewPerYearDto();
            var spendingsTotalUserList = new ArrayList<SpendingsOverviewUserDto>();

            spendingsOverviewPerYearDto.setSumTotalYear(roundValue(getTotalAmountForYear(carts, year)));
            spendingsOverviewPerYearDto.setYear(year);

            for (Long userId : userIds) {
                double sum = 0.0;
                double sumAveragePerMember = 0.0;

                var validMemberships = membership.stream()
                        .filter(gmh -> gmh.getUserId().equals(userId))
                        .toList();

                for (Cart cart : carts) {
                    if (cart.getDatePurchased().toInstant().atZone(ZoneId.systemDefault()).getYear() == year) {
                        if (cart.getUser().getId().equals(userId)) {
                            sum += cart.getAmount();
                        }

                        for (GroupMembershipHistory gmh : validMemberships) {
                            if (hasValidMembership(cart.getDatePurchased(), gmh)) {
                                sumAveragePerMember += cart.getAveragePerMember();
                                break; // Verhindert Mehrfachzählung!
                            }
                        }
                    }
                }

                double diff = sum - sumAveragePerMember;
                var userName = dataLoaderService.loadUser(userId).getName();
                userName = userIsCurrentlyMember(userId, groupId) ? userName : UserEnum.USER_REMOVED.getName();
                if (wasUserMemberInYear(validMemberships, year)) {
                    spendingsTotalUserList.add(new SpendingsOverviewUserDto(userId, userName, roundValue(sum), roundValue(diff)));
                }
            }
            spendingsOverviewPerYearDto.setSpendingsYearlyUser(spendingsTotalUserList);
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
        var spendingsOverviewTotalYearDto = new SpendingsOverviewTotalYearDto();
        var spendingsTotalUserList = new ArrayList<SpendingsOverviewUserDto>();

        spendingsOverviewTotalYearDto.setSumTotalYear(roundValue(getTotalAmountForYear(carts, year)));

        if (hasCartsForYear(carts, year)) {
            for (Long userId : userIds) {
                double sum = 0.0;
                double sumAveragePerMember = 0.0;

                var validMemberships = membership.stream()
                        .filter(gmh -> gmh.getUserId().equals(userId))
                        .toList();

                for (Cart cart : carts) {
                    if (cart.getDatePurchased().toInstant().atZone(ZoneId.systemDefault()).getYear() == year) {
                        if (cart.getUser().getId().equals(userId)) {
                            sum += cart.getAmount();
                        }

                        for (GroupMembershipHistory gmh : validMemberships) {
                            if (hasValidMembership(cart.getDatePurchased(), gmh)) {
                                sumAveragePerMember += cart.getAveragePerMember();
                                break; // Verhindert Mehrfachzählung!
                            }
                        }
                    }
                }

                double diff = sum - sumAveragePerMember;
                var userName = dataLoaderService.loadUser(userId).getName();
                userName = userIsCurrentlyMember(userId, groupId) ? userName : UserEnum.USER_REMOVED.getName();
                if (wasUserMemberInYear(validMemberships, year)) {
                    spendingsTotalUserList.add(new SpendingsOverviewUserDto(userId, userName, roundValue(sum), roundValue(diff)));
                }
            }
        }

        spendingsOverviewTotalYearDto.setSpendingsTotalUser(spendingsTotalUserList);

        return spendingsOverviewTotalYearDto;
    }

    private List<SpendingsOverviewPerMonthDto> getSpendingsOverviewPerMonth(int year, Long groupId) throws UserNotFoundException {
        var membership = dataLoaderService.loadMembershipHistoryForGroup(groupId);
        var carts = dataLoaderService.loadCartListForGroup(groupId);
        var userIds = membership.stream().map(GroupMembershipHistory::getUserId).distinct().toList();
        var spendingsOverviewPerMonthDtoList = new ArrayList<SpendingsOverviewPerMonthDto>();
        var availableMonth = getAvailableMonthsForYear(carts, year);

        for (Integer month : availableMonth) {
            var spendingsOverviewPerMonthDto = new SpendingsOverviewPerMonthDto();
            var spendingsTotalUserList = new ArrayList<SpendingsOverviewUserDto>();

            spendingsOverviewPerMonthDto.setMonth(month);
            spendingsOverviewPerMonthDto.setMonthName(getMonthName(month));
            spendingsOverviewPerMonthDto.setSumTotalMonth(getTotalAmountForMonth(carts, year, month));

            for (Long userId : userIds) {
                double sum = 0.0;
                double sumAveragePerMember = 0.0;

                var validMemberships = membership.stream()
                        .filter(gmh -> gmh.getUserId().equals(userId))
                        .toList();

                for (Cart cart : carts) {
                    if (cart.getDatePurchased().toInstant().atZone(ZoneId.systemDefault()).getMonthValue() == month
                        && cart.getDatePurchased().toInstant().atZone(ZoneId.systemDefault()).getYear() == year) {
                        if (cart.getUser().getId().equals(userId)) {
                            sum += cart.getAmount();
                        }

                        for (GroupMembershipHistory gmh : validMemberships) {
                            if (hasValidMembership(cart.getDatePurchased(), gmh)) {
                                sumAveragePerMember += cart.getAveragePerMember();
                                break; // Verhindert Mehrfachzählung!
                            }
                        }
                    }
                }

                double diff = sum - sumAveragePerMember;
                var userName = dataLoaderService.loadUser(userId).getName();
                userName = userIsCurrentlyMember(userId, groupId) ? userName : UserEnum.USER_REMOVED.getName();
                if (wasUserMemberInMonth(validMemberships, year, month)) {
                    spendingsTotalUserList.add(new SpendingsOverviewUserDto(userId, userName, roundValue(sum), roundValue(diff)));
                }
            }
            spendingsOverviewPerMonthDto.setSpendingsMonthlyUser(spendingsTotalUserList);
            spendingsOverviewPerMonthDtoList.add(spendingsOverviewPerMonthDto);
        }

        return spendingsOverviewPerMonthDtoList;
    }

    // Helper Methods
    private boolean hasValidMembership(Date datePurchased, GroupMembershipHistory gmh) {
        if (datePurchased == null || gmh == null || gmh.getMembershipStart() == null) {
            return false;
        }
        // Zeitkomponente entfernen
        LocalDate purchasedDate = datePurchased.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate startDate = gmh.getMembershipStart().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate endDate = (gmh.getMembershipEnd() != null)
                ? gmh.getMembershipEnd().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                : LocalDate.of(2999, 12, 31);

        return !purchasedDate.isBefore(startDate) && !purchasedDate.isAfter(endDate);
    }

    private Double roundValue(Double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public double getTotalAmountForYear(List<Cart> carts, int year) {
        return carts.stream()
                .filter(cart -> {
                    LocalDate date = cart.getDatePurchased().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    return date.getYear() == year;
                })
                .mapToDouble(Cart::getAmount)
                .sum();
    }

    public List<Integer> getAvailableMonthsForYear(List<Cart> carts, int year) {
        return carts.stream()
                .map(cart -> cart.getDatePurchased().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()) // Datum umwandeln
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
        return carts.stream()
                .filter(cart -> {
                    LocalDate date = cart.getDatePurchased().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    return date.getYear() == year && date.getMonthValue() == month;
                })
                .mapToDouble(Cart::getAmount)
                .sum();
    }

    public boolean hasCartsForYear(List<Cart> carts, int year) {
        return carts.stream()
                .map(cart -> cart.getDatePurchased().toInstant().atZone(ZoneId.systemDefault()).toLocalDate())
                .anyMatch(date -> date.getYear() == year);
    }

    public boolean hasCartsForGroup(List<Cart> carts, Long groupId) {
        return carts.stream()
                .anyMatch(cart -> cart.getGroup().getId() == groupId);
    }

    public boolean wasUserMemberInYear(List<GroupMembershipHistory> membershipHistory, int year) {
        return membershipHistory.stream()
                .anyMatch(gmh -> {
                    LocalDate start = gmh.getMembershipStart().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    LocalDate end = (gmh.getMembershipEnd() != null)
                            ? gmh.getMembershipEnd().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                            : LocalDate.of(2999, 12, 31); // Falls `membership_end` null ist

                    // Prüfen, ob das Jahr innerhalb des Mitgliedschaftszeitraums liegt
                    return (start.getYear() <= year && end.getYear() >= year);
                });
    }

    public boolean wasUserMemberInMonth(List<GroupMembershipHistory> membershipHistory, int year, int month) {
        return membershipHistory.stream()
                .anyMatch(gmh -> {
                    LocalDate start = gmh.getMembershipStart().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    LocalDate end = (gmh.getMembershipEnd() != null)
                            ? gmh.getMembershipEnd().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                            : LocalDate.of(2999, 12, 31); // Falls `membership_end` null ist

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

}
