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

    public SpendingsOverviewDto getSpendingsForGroupAndYear(int year, Long groupId) {
        var spendingsOverview = new SpendingsOverviewDto();
        spendingsOverview.setGroupId(groupId);
        spendingsOverview.setYear(year);
        spendingsOverview.setSpendingsTotalYear(getSpendngsOverviewTotalYearDto(year, groupId));
        spendingsOverview.setSpendingsPerMonth(getSpendngsOverviewPerMonthDto(year, groupId));
        return spendingsOverview;
    }

    private List<SpendingsOverviewPerMonthDto> getSpendngsOverviewPerMonthDto(int year, Long groupId) {
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
                    spendingsPerUser.setUserName(userRepository.findById(spendings.getUserId()).get().getUserName());
                    spendingsPerUser.setSum(spendings.getSumAmount());
                    spendingsPerUser.setDiff(spendings.getDiff());
                    userList.add(spendingsPerUser);
                    spendingsPerMonth.setSpendingsMonthlyUser(userList);
                }
            }
        }
        return spendingsPerMonthList;
    }

    private SpendingsOverviewTotalYearDto getSpendngsOverviewTotalYearDto(int year, Long groupId) {
        var spendingsTotalYear = new SpendingsOverviewTotalYearDto();
        var spendingsSumAverageDiffPerUser = cartRepository.getSpendingsAmountAverageDiffPerUser(year, groupId);
        var userList = new ArrayList<SpendingsOverviewUserDto>();

        for (SpendingsOverviewAmountAverageDiffPerUserDto spendings : spendingsSumAverageDiffPerUser) {
            var spendingsPerUser = new SpendingsOverviewUserDto();
            spendingsPerUser.setUserId(spendings.getUserId());
            //TODO: Wenn Nutzer gelöscht wird, schlägt nachfolgende Zeile fehl -> User-Name in membership-history speichern?
            spendingsPerUser.setUserName(userRepository.findById(spendings.getUserId()).get().getUserName());
            spendingsPerUser.setSum(spendings.getSumAmount());
            spendingsPerUser.setDiff(spendings.getDiff());
            userList.add(spendingsPerUser);
        }

        spendingsTotalYear.setSumTotalYear(getTotalAmountPerYear(year, groupId));
        spendingsTotalYear.setSpendingsTotalUser(userList);
        return spendingsTotalYear;
    }

    private Double getTotalAmountPerYear(int year, Long groupId) {
        return cartRepository.getSpendingsMonthlyTotalSumAmount(year, groupId)
                .stream().mapToDouble(SpendingsOverviewMonthlyTotalSumAmountDto::getSumAmountTotalPerMonth).sum();
    }

    private String getMonthName(int month) {
        return new DateFormatSymbols().getMonths()[month - 1];
    }

    // TODO: Derzeit angemeldete Nutzer -> Spring Security
    private User getCurrentUser() throws UserNotFoundException {
        // Sven als Nutzer, id = 1
        var userId = UserEnum.CURRENT_USER.getId();
        return userRepository.findById(userId).
                orElseThrow(() -> new UserNotFoundException("User with id " + userId + " not found"));
    }
}
