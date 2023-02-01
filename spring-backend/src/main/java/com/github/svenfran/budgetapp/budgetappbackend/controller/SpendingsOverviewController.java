package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.dto.SpendingsOverviewDto;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.GroupNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOrMemberOfGroupException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.service.SpendingsOverviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SpendingsOverviewController {

    @Autowired
    private SpendingsOverviewService spendingsOverviewService;

    @GetMapping("/spendings/{groupId}/{year}")
    public ResponseEntity<SpendingsOverviewDto> getSpendingsForGroupAndYear(@PathVariable("groupId") Long groupId, @PathVariable("year") int year) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        SpendingsOverviewDto spendingsOverview = spendingsOverviewService.getSpendingsForGroupAndYear(year, groupId);
        return new ResponseEntity<>(spendingsOverview, HttpStatus.OK);
    }

    @GetMapping("/spendings/{groupId}")
    public ResponseEntity<SpendingsOverviewDto> getSpendingsForGroupAndAllYears(@PathVariable("groupId") Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        SpendingsOverviewDto spendingsOverviewYearly = spendingsOverviewService.getSpendingsForGroupAndAllYears(groupId);
        return new ResponseEntity<>(spendingsOverviewYearly, HttpStatus.OK);
    }

    @GetMapping("/spendings/available-years/{groupId}")
    public ResponseEntity<List<Integer>> getAvailableYears(@PathVariable("groupId") Long groupId) throws UserNotFoundException {
        List<Integer> availableYears = spendingsOverviewService.getAvailableYears(groupId);
        return new ResponseEntity<>(availableYears, HttpStatus.OK);
    }
}
