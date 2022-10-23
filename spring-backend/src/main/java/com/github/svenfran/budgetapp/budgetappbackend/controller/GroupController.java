package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.GroupNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.UserNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupMembersDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupOverviewDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupSideNavDto;
import com.github.svenfran.budgetapp.budgetappbackend.service.GroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class GroupController {

    @Autowired
    private GroupService groupService;

    @GetMapping("/grouplist-sidenav")
    public ResponseEntity<List<GroupSideNavDto>> getGroupsForSideNav() throws UserNotFoundException {
        List<GroupSideNavDto> groups = groupService.getGroupsForSideNav();
        return new ResponseEntity<>(groups, HttpStatus.OK);
    }

    @GetMapping("/grouplist-groupoverview")
    public ResponseEntity<List<GroupOverviewDto>> getGroupsForGroupOverview() throws UserNotFoundException {
        List<GroupOverviewDto> groups = groupService.getGroupsForGroupOverview();
        return new ResponseEntity<>(groups, HttpStatus.OK);
    }

    @GetMapping("/groups-with-members")
    public ResponseEntity<List<GroupMembersDto>> getGroupsWithMembers() throws UserNotFoundException {
        List<GroupMembersDto> groups = groupService.getGroupsWithMembers();
        return new ResponseEntity<>(groups, HttpStatus.OK);
    }

    @GetMapping("/groups/{id}")
    public ResponseEntity<GroupMembersDto> getGroupMembers(@PathVariable("id") Long id) throws UserNotFoundException, GroupNotFoundException {
        GroupMembersDto groups = groupService.getGroupMembers(id);
        return new ResponseEntity<>(groups, HttpStatus.OK);
    }

    @PostMapping("/group/add")
    public ResponseEntity<GroupDto> addGroup(@RequestBody GroupDto groupDto) throws UserNotFoundException {
        GroupDto newGroup = groupService.addGroup(groupDto);
        return new ResponseEntity<>(newGroup, HttpStatus.CREATED);
    }
}
