package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.GroupNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.UserNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.dao.GroupRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dao.UserRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupMembersDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupOverviewDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupSideNavDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.service.mapper.GroupDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;

@Service
public class GroupService {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupDtoMapper groupDtoMapper;

    public List<GroupSideNavDto> getGroupsForSideNav() throws UserNotFoundException {
        var user = getCurrentUser();
        var member = new HashSet<User>();
        member.add(user);
        List<Group> groups = groupRepository.findGroupsByMembersInOrOwnerOrderById(member, user);
        return groups.stream().map(GroupSideNavDto::new).toList();
    }

    public List<GroupOverviewDto> getGroupsForGroupOverview() throws UserNotFoundException {
        var user = getCurrentUser();
        var member = new HashSet<User>();
        member.add(user);
        List<Group> groups = groupRepository.findGroupsByMembersInOrOwnerOrderById(member, user);
        return groups.stream().map(GroupOverviewDto::new).toList();
    }

    public List<GroupMembersDto> getGroupsWithMembers() throws UserNotFoundException {
        var user = getCurrentUser();
        var member = new HashSet<User>();
        member.add(user);
        List<Group> groups = groupRepository.findGroupsByMembersInOrOwnerOrderById(member, user);
        return groups.stream().map(GroupMembersDto::new).toList();
    }

    public GroupMembersDto getGroupMembers(Long id) throws UserNotFoundException, GroupNotFoundException {
        var groups = getGroupsWithMembers();
        return groups.stream().filter(group -> group.getId().equals(id)).findFirst().
                orElseThrow(() -> new GroupNotFoundException("You are not allowed to access this group!"));
        // return new GroupMembersDto(groupRepository.findById(id).
        //        orElseThrow(() -> new GroupNotFoundException("Group with id " + id + " not found!")));
    }

    public GroupDto addGroup(GroupDto groupDto) throws UserNotFoundException {
        var user = getCurrentUser();
        return new GroupDto(groupRepository.save(groupDtoMapper.GroupDtoToEntity(groupDto, user)));
    }

    // TODO: Derzeit angemeldete Nutzer -> Spring Security
    private User getCurrentUser() throws UserNotFoundException {
        // Sven als Nutzer, id = 1
        var userId = 1L;
        return userRepository.findById(userId).
                orElseThrow(() -> new UserNotFoundException("User with id " + userId + " not found"));
    }

}
