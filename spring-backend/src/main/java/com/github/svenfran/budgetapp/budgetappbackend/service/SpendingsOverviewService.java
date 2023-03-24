package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.*;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.GroupMembershipHistory;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.GroupNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOrMemberOfGroupException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupMembershipHistoryRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

@Service
public class SpendingsOverviewService {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataLoaderService dataLoaderService;


    public SpendingsOverviewDto getSpendingsForGroupAndYear(int year, Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getCurrentUser();
        var group = dataLoaderService.loadGroup(groupId);
        verifyIsPartOfGroup(user, group);

        var spendingsOverview = new SpendingsOverviewDto();
        spendingsOverview.setGroupId(groupId);
        spendingsOverview.setYear(year);
        spendingsOverview.setSpendingsTotalYear(getSpendingsOverviewTotalYear(year, groupId));
        spendingsOverview.setSpendingsPerMonth(getSpendingsOverviewPerMonth(year, groupId));
        spendingsOverview.setAvailableYears(cartRepository.getAvailableYearsForGroup(groupId));
        return spendingsOverview;
    }

    public SpendingsOverviewDto getSpendingsForGroupAndAllYears(Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getCurrentUser();
        var group = dataLoaderService.loadGroup(groupId);
        verifyIsPartOfGroup(user, group);

        var spendingsOverview = new SpendingsOverviewDto();
        spendingsOverview.setGroupId(groupId);
        spendingsOverview.setSpendingsTotalYear(getSpendingsOverviewTotalAllYears(groupId));
        spendingsOverview.setSpendingsPerYear(getSpendingsOverviewPerYear(groupId));
        return spendingsOverview;
    }


    private List<SpendingsOverviewPerMonthDto> getSpendingsOverviewPerMonth(int year, Long groupId) throws UserNotFoundException {
        var spendingsPerMonthList = new ArrayList<SpendingsOverviewPerMonthDto>();
        var spendingsAmountAverageDiffPerMonth = getAmountAverageDiffPerUserAndMonth(groupId, year);
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
        var spendingsAmountAverageDiffPerYear = getAmountAverageDiffPerUserYearly(groupId);
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
        var spendingsSumAverageDiffPerUser = getAmountAverageDiffPerUserAndYear(groupId, year);
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
        var spendingsSumAverageDiffPerUserYearly = getAmountAverageDiffPerUserAndTotalYears(groupId);
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


    private List<SpendingsOverviewAmountAverageDiffPerMonthDto> getAmountAverageDiffPerUserAndMonth(Long groupId, int year) {
        var amount = cartRepository.getSpendingsAmountPerMonthAndUser(year, groupId);
        var average = cartRepository.getAveragePerUserAndYear(groupId, year);

        var spendingsAmountAverageDiffPerMonthList = new ArrayList<SpendingsOverviewAmountAverageDiffPerMonthDto>();

        for (SpendingsOverviewAmountAverageDiffPerMonthDto amt : amount) {
            var spendingsAmountAverageDiffPerMonth = new SpendingsOverviewAmountAverageDiffPerMonthDto();
            Double sumAverage = 0.0;
            for (SpendingsOverviewAverageDto avg : average) {
                if (avg.getMonth() == amt.getMonth() && avg.getUserId().equals(amt.getUserId())) {
                    spendingsAmountAverageDiffPerMonth.setMonth(amt.getMonth());
                    spendingsAmountAverageDiffPerMonth.setYear(amt.getYear());
                    spendingsAmountAverageDiffPerMonth.setUserId(amt.getUserId());
                    spendingsAmountAverageDiffPerMonth.setSumAmount(amt.getSumAmount());
                    spendingsAmountAverageDiffPerMonth.setSumAveragePerMember(sumAverage += avg.getAveragePerMember());
                    spendingsAmountAverageDiffPerMonth.setDiff(amt.getSumAmount() - sumAverage);
                }
            }
            spendingsAmountAverageDiffPerMonthList.add(spendingsAmountAverageDiffPerMonth);
        }
        return spendingsAmountAverageDiffPerMonthList;
    }

    private List<SpendingsOverviewAmountAverageDiffPerUserDto> getAmountAverageDiffPerUserAndYear(Long groupId, int year) {
        var amount = cartRepository.getSpendingsAmountPerYearAndUser(year, groupId);
        var average = cartRepository.getAveragePerUserAndYear(groupId, year);

        var spendingsAmountAverageDiffPerYearList = new ArrayList<SpendingsOverviewAmountAverageDiffPerUserDto>();

        for (SpendingsOverviewAmountAverageDiffPerUserDto amt : amount) {
            var spendingsAmountAverageDiffPerYear = new SpendingsOverviewAmountAverageDiffPerUserDto();
            Double sumAverage = 0.0;
            for (SpendingsOverviewAverageDto avg : average) {
                if (avg.getUserId().equals(amt.getUserId())) {
                    spendingsAmountAverageDiffPerYear.setYear(amt.getYear());
                    spendingsAmountAverageDiffPerYear.setUserId(amt.getUserId());
                    spendingsAmountAverageDiffPerYear.setSumAmount(amt.getSumAmount());
                    spendingsAmountAverageDiffPerYear.setSumAveragePerMember(sumAverage += avg.getAveragePerMember());
                    spendingsAmountAverageDiffPerYear.setDiff(amt.getSumAmount() - sumAverage);
                }
            }
            spendingsAmountAverageDiffPerYearList.add(spendingsAmountAverageDiffPerYear);
        }
        return spendingsAmountAverageDiffPerYearList;
    }

    private List<SpendingsOverviewAmountAverageDiffPerYearDto> getAmountAverageDiffPerUserYearly(Long groupId) {
        var amount = cartRepository.getSpendingsAmountPerUserYearly(groupId);
        var average = cartRepository.getAveragePerUserAndTotalYears(groupId);

        var spendingsAmountAverageDiffPerYearList = new ArrayList<SpendingsOverviewAmountAverageDiffPerYearDto>();

        for (SpendingsOverviewAmountAverageDiffPerUserDto amt : amount) {
            var spendingsAmountAverageDiffPerYear = new SpendingsOverviewAmountAverageDiffPerYearDto();
            Double sumAverage = 0.0;
            for (SpendingsOverviewAverageDto avg : average) {
                if (avg.getYear() == amt.getYear() && avg.getUserId().equals(amt.getUserId())) {
                    spendingsAmountAverageDiffPerYear.setYear(amt.getYear());
                    spendingsAmountAverageDiffPerYear.setUserId(amt.getUserId());
                    spendingsAmountAverageDiffPerYear.setSumAmount(amt.getSumAmount());
                    spendingsAmountAverageDiffPerYear.setSumAveragePerMember(sumAverage += avg.getAveragePerMember());
                    spendingsAmountAverageDiffPerYear.setDiff(amt.getSumAmount() - sumAverage);
                }
            }
            spendingsAmountAverageDiffPerYearList.add(spendingsAmountAverageDiffPerYear);
        }
        return spendingsAmountAverageDiffPerYearList;
    }

    private List<SpendingsOverviewAmountAverageDiffPerUserDto> getAmountAverageDiffPerUserAndTotalYears(Long groupId) {
        var amount = cartRepository.getSpendingsAmountPerUserAndTotalYears(groupId);
        var average = cartRepository.getAveragePerUserAndTotalYears(groupId);

        var spendingsAmountAverageDiffTotalYearsList = new ArrayList<SpendingsOverviewAmountAverageDiffPerUserDto>();

        for (SpendingsOverviewAmountAverageDiffPerUserDto amt : amount) {
            var spendingsAmountAverageDiffTotalYears = new SpendingsOverviewAmountAverageDiffPerUserDto();
            Double sumAverage = 0.0;
            for (SpendingsOverviewAverageDto avg : average) {
                if (avg.getUserId().equals(amt.getUserId())) {
                    spendingsAmountAverageDiffTotalYears.setUserId(amt.getUserId());
                    spendingsAmountAverageDiffTotalYears.setSumAmount(amt.getSumAmount());
                    spendingsAmountAverageDiffTotalYears.setSumAveragePerMember(sumAverage += avg.getAveragePerMember());
                    spendingsAmountAverageDiffTotalYears.setDiff(amt.getSumAmount() - sumAverage);
                }
            }
            spendingsAmountAverageDiffTotalYearsList.add(spendingsAmountAverageDiffTotalYears);
        }
        return spendingsAmountAverageDiffTotalYearsList;
    }


    private void verifyIsPartOfGroup(User user, Group group) throws NotOwnerOrMemberOfGroupException {
        if (!(group.getOwner().equals(user) || group.getMembers().contains(user))) {
            throw new NotOwnerOrMemberOfGroupException("User with ID " + user.getId() + " is either a member nor the owner of the group");
        }
    }

}
