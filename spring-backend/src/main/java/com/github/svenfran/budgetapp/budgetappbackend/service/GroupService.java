package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.dao.GroupRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dao.UserRepository;
import com.github.svenfran.budgetapp.budgetappbackend.dto.*;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.service.mapper.GroupDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

@Service
public class GroupService {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupDtoMapper groupDtoMapper;

    public List<Group> getGroupsByMemberOrOwner() throws UserNotFoundException {
        var user = getCurrentUser();
        var member = new HashSet<User>();
        member.add(user);
        List<Group> groupsMember = groupRepository.findGroupsByMembersInOrderById(member);
        List<Group> groupsOwner = groupRepository.findGroupsByOwnerOrderById(user);
        return Stream.concat(groupsOwner.stream(), groupsMember.stream()).toList();
    }

    public List<GroupSideNavDto> getGroupsForSideNav() throws UserNotFoundException {
        return getGroupsByMemberOrOwner().stream().map(GroupSideNavDto::new).toList();
    }

    public List<GroupOverviewDto> getGroupsForGroupOverview() throws UserNotFoundException {
        return getGroupsByMemberOrOwner().stream().map(GroupOverviewDto::new).toList();
    }

    public List<GroupMembersDto> getGroupsWithMembers() throws UserNotFoundException {
        return getGroupsByMemberOrOwner().stream().map(GroupMembersDto::new).toList();
    }

    public GroupMembersDto getGroupMembers(Long groupId) throws UserNotFoundException, GroupNotFoundException {
        var groups = getGroupsWithMembers();
        return groups.stream().filter(group -> group.getId().equals(groupId)).findFirst().
                orElseThrow(() -> new GroupNotFoundException("You are not allowed to access this group!"));
//         return new GroupMembersDto(groupRepository.findById(groupId).
//                orElseThrow(() -> new GroupNotFoundException("Group with id " + groupId + " not found!")));
    }

    public GroupDto addGroup(GroupDto groupDto) throws UserNotFoundException {
        var user = getCurrentUser();
        return new GroupDto(groupRepository.save(groupDtoMapper.GroupDtoToEntity(groupDto, user)));
    }

    public GroupDto updateGroup(GroupDto groupDto) throws UserNotFoundException {
        var user = getCurrentUser();
        return new GroupDto(groupRepository.save(groupDtoMapper.GroupDtoToEntity(groupDto, user)));
    }

    public GroupMembersDto addMemberToGroup(AddGroupMemberDto addGroupMemberDto) throws GroupNotFoundException, UserNotFoundException, NotOwnerOfGroupException, MemberAlreadyExixtsException, MemberEqualsOwnerException {
        var user = getCurrentUser();
        var newMember = userRepository.findByEmail(addGroupMemberDto.getNewMemberEmail());
        var group = groupRepository.findById(addGroupMemberDto.getId()).
                orElseThrow(() -> new GroupNotFoundException("Add new member: Group not found!"));

        if (newMember == null) {
            throw new UserNotFoundException("Add new member: User not found");
        } else if (!user.equals(group.getOwner())) {
            throw new NotOwnerOfGroupException("Add new member: You are not the owner of the Group");
        } else if (group.getMembers().contains(newMember)) {
            throw new MemberAlreadyExixtsException("Add new member: Member already exists");
        } else if (newMember.equals(user)) {
            throw new MemberEqualsOwnerException("Add new member: New member equals group owner");
        } else {
            group.getMembers().add(newMember);
            newMember.getGroups().add(group);
        }

        return new GroupMembersDto(groupRepository.save(group));
    }

    public GroupMembersDto removeMemberFromGroup(RemoveGroupMemberDto removeGroupMemberDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        var user = getCurrentUser();
        var removedMember = userRepository.findById(removeGroupMemberDto.getMember().getId()).
                orElseThrow(() -> new UserNotFoundException("Remove member: User not found"));
        var group = groupRepository.findById(removeGroupMemberDto.getId()).
                orElseThrow(() -> new GroupNotFoundException("Remove member: Group not found!"));

        group.getMembers().remove(removedMember);
        removedMember.getGroups().remove(group);

//        if (user.equals(group.getOwner())) {
//        group.getMembers().remove(removedMember);
//        removedMember.getGroups().remove(group);
//        } else {
//            throw new NotOwnerOfGroupException("Remove member: You are not the owner of the Group");
//        }
        return new GroupMembersDto(groupRepository.save(group));
    }

    public void deleteGroup(Long id) {
        groupRepository.deleteById(id);
    }

    // TODO: Derzeit angemeldete Nutzer -> Spring Security
    private User getCurrentUser() throws UserNotFoundException {
        // Sven als Nutzer, id = 1
        var userId = 1L;
        return userRepository.findById(userId).
                orElseThrow(() -> new UserNotFoundException("User with id " + userId + " not found"));
    }

}
