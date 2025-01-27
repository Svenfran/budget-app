package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.dto.*;
import com.github.svenfran.budgetapp.budgetappbackend.service.GroupMembershipHistoryService;
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

    @Autowired
    private GroupMembershipHistoryService gmhService;

    @GetMapping("/groups/sidenav")
    public ResponseEntity<List<GroupSideNavDto>> getGroupsForSideNav() throws UserNotFoundException {
        List<GroupSideNavDto> groups = groupService.getGroupsForSideNav();
        return new ResponseEntity<>(groups, HttpStatus.OK);
    }

    @GetMapping("/groups/overview")
    public ResponseEntity<List<GroupOverviewDto>> getGroupsForGroupOverview() throws UserNotFoundException {
        List<GroupOverviewDto> groups = groupService.getGroupsForGroupOverview();
        return new ResponseEntity<>(groups, HttpStatus.OK);
    }

    @GetMapping("/groups/history-by-group-and-user/{groupId}")
    public ResponseEntity<List<GroupMembershipHistoryDto>> getGroupHistoryForGroupAndUser(@PathVariable("groupId") Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        List<GroupMembershipHistoryDto> gmh = gmhService.getGroupHistoryForGroupAndUser(groupId);
        return new ResponseEntity<>(gmh, HttpStatus.OK);
    }

    @GetMapping("/groups/members/{groupId}")
    public ResponseEntity<GroupMembersDto> getGroupMembers(@PathVariable("groupId") Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        GroupMembersDto groups = groupService.getGroupMembers(groupId);
        return new ResponseEntity<>(groups, HttpStatus.OK);
    }

    @PostMapping("/groups/add")
    public ResponseEntity<GroupDto> addGroup(@RequestBody GroupDto groupDto) throws UserNotFoundException {
        GroupDto newGroup = groupService.addGroup(groupDto);
        return new ResponseEntity<>(newGroup, HttpStatus.CREATED);
    }

    @PostMapping("/groups/add-new-member")
    public ResponseEntity<GroupMembersDto> addMemberToGroup(@RequestBody AddGroupMemberDto addGroupMemberDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException, MemberAlreadyExixtsException, MemberEqualsOwnerException {
        GroupMembersDto newGroup = groupService.addMemberToGroup(addGroupMemberDto);
        return new ResponseEntity<>(newGroup, HttpStatus.CREATED);
    }

    @PostMapping("/groups/remove-member-from-group")
    public ResponseEntity<GroupMembersDto> removeMemberFromGroup(@RequestBody RemoveGroupMemberDto removeGroupMemberDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        GroupMembersDto newGroup = groupService.removeMemberFromGroup(removeGroupMemberDto);
        return new ResponseEntity<>(newGroup, HttpStatus.CREATED);
    }

    @PutMapping("/groups/update")
    public ResponseEntity<GroupDto> updateGroup(@RequestBody GroupDto groupDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        GroupDto updatedGroup = groupService.updateGroup(groupDto);
        return new ResponseEntity<>(updatedGroup, HttpStatus.OK);
    }

    @PostMapping("/groups/change-groupowner")
    public ResponseEntity<ChangeGroupOwnerDto> changeGroupOwner(@RequestBody ChangeGroupOwnerDto changeGroupOwnerDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        groupService.changeGroupOwner(changeGroupOwnerDto);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @DeleteMapping("/groups/delete/{id}")
    public ResponseEntity<GroupOverviewDto> deleteGroup(@PathVariable("id") Long id) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        groupService.deleteGroup(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
