package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.dto.*;
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

    @GetMapping("/groups/members/{groupId}")
    public ResponseEntity<GroupMembersDto> getGroupMembers(@PathVariable("groupId") Long groupId) throws UserNotFoundException, GroupNotFoundException {
        GroupMembersDto groups = groupService.getGroupMembers(groupId);
        return new ResponseEntity<>(groups, HttpStatus.OK);
    }

    @PostMapping("/groups/add")
    public ResponseEntity<GroupDto> addGroup(@RequestBody GroupDto groupDto) throws UserNotFoundException {
        GroupDto newGroup = groupService.addGroup(groupDto);
        return new ResponseEntity<>(newGroup, HttpStatus.CREATED);
    }

    @PutMapping("/groups/add-new-member")
    public ResponseEntity<GroupMembersDto> addMemberToGroup(@RequestBody AddGroupMemberDto addGroupMemberDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException, MemberAlreadyExixtsException, MemberEqualsOwnerException {
        GroupMembersDto newGroup = groupService.addMemberToGroup(addGroupMemberDto);
        return new ResponseEntity<>(newGroup, HttpStatus.CREATED);
    }

//    @PutMapping("/groups/remove-memeber-from-group")
    @RequestMapping(value = "/groups/remove-member-from-group", method = RequestMethod.PUT)
    public ResponseEntity<GroupMembersDto> removeMemberFromGroup(@RequestBody RemoveGroupMemberDto removeGroupMemberDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        GroupMembersDto newGroup = groupService.removeMemberFromGroup(removeGroupMemberDto);
        return new ResponseEntity<>(newGroup, HttpStatus.CREATED);
    }

    @PutMapping("/groups/update")
    public ResponseEntity<GroupDto> updateGroup(@RequestBody GroupDto groupDto) throws UserNotFoundException {
        GroupDto updatedGroup = groupService.updateGroup(groupDto);
        return new ResponseEntity<>(updatedGroup, HttpStatus.CREATED);
    }

    @DeleteMapping("/groups/delete/{id}")
    public ResponseEntity<GroupOverviewDto> deleteGroup(@PathVariable("id") Long id) {
        groupService.deleteGroup(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
