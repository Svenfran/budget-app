package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.dto.SpendingsOverviewDto;
import com.github.svenfran.budgetapp.budgetappbackend.service.SpendingsOverviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SpendingsOverviewController {

    @Autowired
    private SpendingsOverviewService spendingsOverviewService;

    @GetMapping("/spendings/{groupId}/{year}")
    public ResponseEntity<SpendingsOverviewDto> getSpendingsForGroupAndYear(@PathVariable("groupId") Long groupId, @PathVariable("year") int year) {
        SpendingsOverviewDto spendingsOverview = spendingsOverviewService.getSpendingsForGroupAndYear(year, groupId);
        return new ResponseEntity<>(spendingsOverview, HttpStatus.OK);
    }
}
