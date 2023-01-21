package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.constants.UserEnum;
import com.github.svenfran.budgetapp.budgetappbackend.dto.*;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.List;

@Service
public class SpendingsOverviewService {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private UserRepository userRepository;

    public SpendingsOverviewDto getSpendingsForGroupAndYear(int year, Long groupId) throws UserNotFoundException {
        var spendingsOverview = new SpendingsOverviewDto();
        spendingsOverview.setGroupId(groupId);
        spendingsOverview.setYear(year);
        spendingsOverview.setSpendingsTotalYear(getSpendingsOverviewTotalYear(year, groupId));
        spendingsOverview.setSpendingsPerMonth(getSpendingsOverviewPerMonth(year, groupId));
        spendingsOverview.setAvailableYears(cartRepository.getAvailableYearsForGroup(groupId));
        return spendingsOverview;
    }

    public SpendingsOverviewDto getSpendingsForGroupAndAllYears(Long groupId) throws UserNotFoundException {
        var spendingsOverview = new SpendingsOverviewDto();
        spendingsOverview.setGroupId(groupId);
        spendingsOverview.setSpendingsTotalYear(getSpendingsOverviewTotalAllYears(groupId));
        spendingsOverview.setSpendingsPerYear(getSpendingsOverviewPerYear(groupId));
        return spendingsOverview;
    }


    private List<SpendingsOverviewPerMonthDto> getSpendingsOverviewPerMonth(int year, Long groupId) throws UserNotFoundException {
        var spendingsPerMonthList = new ArrayList<SpendingsOverviewPerMonthDto>();
        var spendingsAmountAverageDiffPerMonth = cartRepository.getSpendingsAmountAverageDiffPerMonth(year, groupId);
        var spendingsMonthly = cartRepository.getSpendingsMonthlyTotalSumAmount(year, groupId);

        for (SpendingsOverviewMonthlyTotalSumAmountDto spendings : spendingsMonthly) {
            var spendingsPerMonth = new SpendingsOverviewPerMonthDto();
            spendingsPerMonth.setMonth(spendings.getMonth());
            spendingsPerMonth.setMonthName(getMonthName(spendings.getMonth()));
            spendingsPerMonth.setSumTotalMonth(spendings.getSumAmountTotalPerMonth());
            spendingsPerMonthList.add(spendingsPerMonth);
        }

        for (SpendingsOverviewPerMonthDto spendingsPerMonth : spendingsPerMonthList) {
            var userList = new ArrayList<SpendingsOverviewUserDto>();
            for (SpendingsOverviewAmountAverageDiffPerMonthDto spendings : spendingsAmountAverageDiffPerMonth) {
                if (spendingsPerMonth.getMonth() == spendings.getMonth()) {
                    var spendingsPerUser = new SpendingsOverviewUserDto();
                    spendingsPerUser.setUserId(spendings.getUserId());
                    //TODO: Wenn Nutzer gelöscht wird, schlägt nachfolgende Zeile fehl -> User-Name in membership-history speichern?
                    spendingsPerUser.setUserName(userRepository.findById(spendings.getUserId())
                            .orElseThrow(() -> new UserNotFoundException("Get SpendingsOverviewPerMonth: User not found")).getUserName());
                    spendingsPerUser.setSum(spendings.getSumAmount());
                    spendingsPerUser.setDiff(spendings.getDiff());
                    userList.add(spendingsPerUser);
                    spendingsPerMonth.setSpendingsMonthlyUser(userList);
                }
            }
        }
        return spendingsPerMonthList;
    }

    private List<SpendingsOverviewPerYearDto> getSpendingsOverviewPerYear(Long groupId) throws UserNotFoundException {
        var spendingsPerYearList = new ArrayList<SpendingsOverviewPerYearDto>();
        var spendingsAmountAverageDiffPerYear = cartRepository.getSpendingsAmountAverageDiffPerYear(groupId);
        var spendingsYearly = cartRepository.getSpendingsYearlyTotalSumAmount(groupId);

        for (SpendingsOverviewYearlyTotalSumAmountDto spendings : spendingsYearly) {
            var spendingsPerYear = new SpendingsOverviewPerYearDto();
            spendingsPerYear.setYear(spendings.getYear());
            spendingsPerYear.setSumTotalYear(spendings.getSumAmountTotalPerYear());
            spendingsPerYearList.add(spendingsPerYear);
        }

        for (SpendingsOverviewPerYearDto spendingsPerYear : spendingsPerYearList) {
            var userList = new ArrayList<SpendingsOverviewUserDto>();
            for (SpendingsOverviewAmountAverageDiffPerYearDto spendings : spendingsAmountAverageDiffPerYear) {
                if (spendingsPerYear.getYear() == spendings.getYear()) {
                    var spendingsPerUser = new SpendingsOverviewUserDto();
                    spendingsPerUser.setUserId(spendings.getUserId());
                    //TODO: Wenn Nutzer gelöscht wird, schlägt nachfolgende Zeile fehl -> User-Name in membership-history speichern?
                    spendingsPerUser.setUserName(userRepository.findById(spendings.getUserId())
                            .orElseThrow(() -> new UserNotFoundException("Get SpendingsOverviewPerYear: User not found")).getUserName());
                    spendingsPerUser.setSum(spendings.getSumAmount());
                    spendingsPerUser.setDiff(spendings.getDiff());
                    userList.add(spendingsPerUser);
                    spendingsPerYear.setSpendingsYearlyUser(userList);
                }
            }
        }

        return spendingsPerYearList;
    }


    private SpendingsOverviewTotalYearDto getSpendingsOverviewTotalYear(int year, Long groupId) throws UserNotFoundException {
        var spendingsTotalYear = new SpendingsOverviewTotalYearDto();
        var spendingsSumAverageDiffPerUser = cartRepository.getSpendingsAmountAverageDiffPerUser(year, groupId);
        var userList = new ArrayList<SpendingsOverviewUserDto>();

        for (SpendingsOverviewAmountAverageDiffPerUserDto spendings : spendingsSumAverageDiffPerUser) {
            var spendingsPerUser = new SpendingsOverviewUserDto();
            spendingsPerUser.setUserId(spendings.getUserId());
            //TODO: Wenn Nutzer gelöscht wird, schlägt nachfolgende Zeile fehl -> User-Name in membership-history speichern?
            spendingsPerUser.setUserName(userRepository.findById(spendings.getUserId())
                    .orElseThrow(() -> new UserNotFoundException("Get SpendingsOverviewTotalYearDto: User not found")).getUserName());
            spendingsPerUser.setSum(spendings.getSumAmount());
            spendingsPerUser.setDiff(spendings.getDiff());
            userList.add(spendingsPerUser);
        }

        spendingsTotalYear.setSumTotalYear(getTotalAmountPerYear(year, groupId));
        spendingsTotalYear.setSpendingsTotalUser(userList);
        return spendingsTotalYear;
    }

    private SpendingsOverviewTotalYearDto getSpendingsOverviewTotalAllYears(Long groupId) throws UserNotFoundException {
        var spendingsTotalAllYears = new SpendingsOverviewTotalYearDto();
        var spendingsSumAverageDiffPerUserYearly = cartRepository.getSpendingsAmountAverageDiffPerUserYearly(groupId);
        var userList = new ArrayList<SpendingsOverviewUserDto>();

        for (SpendingsOverviewAmountAverageDiffPerUserDto spendings : spendingsSumAverageDiffPerUserYearly) {
            var spendingsPerUser = new SpendingsOverviewUserDto();
            spendingsPerUser.setUserId(spendings.getUserId());
            //TODO: Wenn Nutzer gelöscht wird, schlägt nachfolgende Zeile fehl -> User-Name in membership-history speichern?
            spendingsPerUser.setUserName(userRepository.findById(spendings.getUserId())
                    .orElseThrow(() -> new UserNotFoundException("Get SpendingsOverviewTotalAllYears: User not found")).getUserName());
            spendingsPerUser.setSum(spendings.getSumAmount());
            spendingsPerUser.setDiff(spendings.getDiff());
            userList.add(spendingsPerUser);
        }

        spendingsTotalAllYears.setSumTotalYear(getTotalAmountForAllYears(groupId));
        spendingsTotalAllYears.setSpendingsTotalUser(userList);
        return spendingsTotalAllYears;
    }


    private Double getTotalAmountPerYear(int year, Long groupId) {
        return cartRepository.getSpendingsMonthlyTotalSumAmount(year, groupId)
                .stream().mapToDouble(SpendingsOverviewMonthlyTotalSumAmountDto::getSumAmountTotalPerMonth).sum();
    }

    private Double getTotalAmountForAllYears(Long groupId) {
        return cartRepository.getTotalAmountAllYears(groupId);
    }


    private String getMonthName(int month) {
        return new DateFormatSymbols().getMonths()[month - 1];
    }

    public List<Integer> getAvailableYears(Long groupId) {
        return cartRepository.getAvailableYearsForGroup(groupId);
    }

    // TODO: Derzeit angemeldete Nutzer -> Spring Security
    private User getCurrentUser() throws UserNotFoundException {
        // Sven als Nutzer, id = 1
        var userId = UserEnum.CURRENT_USER.getId();
        return userRepository.findById(userId).
                orElseThrow(() -> new UserNotFoundException("User with id " + userId + " not found"));
    }


}
