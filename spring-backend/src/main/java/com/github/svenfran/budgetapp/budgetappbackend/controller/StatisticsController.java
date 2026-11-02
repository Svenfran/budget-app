package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.constants.StatisticsPeriod;
import com.github.svenfran.budgetapp.budgetappbackend.dto.StatisticsDto;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.GroupNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.InvalidStatisticsRequestException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOrMemberOfGroupException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.service.StatisticsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsDto> getStatistics(@RequestParam(value = "groupId", required = false) Long groupId,
                                                       @RequestParam("period") StatisticsPeriod period,
                                                       @RequestParam(value = "year", required = false) Integer year,
                                                       @RequestParam(value = "month", required = false) Integer month)
            throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException,
            InvalidStatisticsRequestException {
        StatisticsDto statistics = statisticsService.getStatistics(groupId, period, year, month);
        return new ResponseEntity<>(statistics, HttpStatus.OK);
    }
}
