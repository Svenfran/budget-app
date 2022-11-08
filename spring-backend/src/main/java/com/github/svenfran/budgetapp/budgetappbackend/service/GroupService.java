package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.constants.UserEnum;
import com.github.svenfran.budgetapp.budgetappbackend.dao.*;
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

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ShoppingListRepository shoppingListRepository;

    @Autowired
    private ShoppingItemRepository shoppingItemRepository;

    public Stream<Group> getGroupsByMemberOrOwner() throws UserNotFoundException {
        var user = getCurrentUser();
        var member = new HashSet<User>();
        member.add(user);
        List<Group> groupsMember = groupRepository.findGroupsByMembersInOrderById(member);
        List<Group> groupsOwner = groupRepository.findGroupsByOwnerOrderById(user);
        return Stream.concat(groupsOwner.stream(), groupsMember.stream()).sorted((a,b) -> (int) (a.getId() - b.getId()));
    }

    public List<GroupSideNavDto> getGroupsForSideNav() throws UserNotFoundException {
        return getGroupsByMemberOrOwner().map(GroupSideNavDto::new).toList();
    }

    public List<GroupOverviewDto> getGroupsForGroupOverview() throws UserNotFoundException {
        return getGroupsByMemberOrOwner().map(GroupOverviewDto::new).toList();
    }

    public GroupMembersDto getGroupMembers(Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = getCurrentUser();
        var group = groupRepository.findById(groupId).
                orElseThrow(() -> new GroupNotFoundException("Get Group Members: Group not found"));
        var groupOwner = group.getOwner();
        var groupMembers = group.getMembers();

        if (groupOwner.equals(user) || groupMembers.contains(user)) {
            return new GroupMembersDto(group);
        } else throw new NotOwnerOrMemberOfGroupException("Get Group Members: You are either a member nor the owner of this group");
    }

    public GroupDto addGroup(GroupDto groupDto) throws UserNotFoundException {
        var user = getCurrentUser();
        return new GroupDto(groupRepository.save(groupDtoMapper.GroupDtoToEntity(groupDto, user)));
    }

    public GroupDto updateGroup(GroupDto groupDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        var user = getCurrentUser();
        var group = groupRepository.findById(groupDto.getId()).
                orElseThrow(() -> new GroupNotFoundException("Update Group: Group not found"));
        var groupOwner = userRepository.findById(group.getOwner().getId()).
                orElseThrow(() -> new UserNotFoundException("Update Group: Group owner not found"));

        if (groupOwner.equals(user)) {
            return new GroupDto(groupRepository.save(groupDtoMapper.GroupDtoToEntity(groupDto, groupOwner)));
        } else {
            throw new NotOwnerOfGroupException("Update Group: You are not the owner of the Group");
        }
    }

    public GroupMembersDto addMemberToGroup(AddGroupMemberDto addGroupMemberDto) throws GroupNotFoundException, UserNotFoundException, NotOwnerOfGroupException, MemberAlreadyExixtsException, MemberEqualsOwnerException {
        var user = getCurrentUser();
        var newMember = userRepository.findByEmail(addGroupMemberDto.getNewMemberEmail());

        if (newMember == null) throw new UserNotFoundException("Add new member: User not found");

        var group = groupRepository.findById(addGroupMemberDto.getId()).
                orElseThrow(() -> new GroupNotFoundException("Add new member: Group not found!"));

        if (!user.equals(group.getOwner())) {
            throw new NotOwnerOfGroupException("Add new member: You are not the owner of the Group");
        } else if (group.getMembers().contains(newMember)) {
            throw new MemberAlreadyExixtsException("Add new member: Member already exists");
        } else if (newMember.equals(user)) {
            throw new MemberEqualsOwnerException("Add new member: New member equals group owner");
        } else {
            group.addMember(newMember);
        }
        return new GroupMembersDto(groupRepository.save(group));
    }

    public GroupMembersDto removeMemberFromGroup(RemoveGroupMemberDto removeGroupMemberDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        var user = getCurrentUser();
        var removedMember = userRepository.findById(removeGroupMemberDto.getMember().getId()).
                orElseThrow(() -> new UserNotFoundException("Remove member: User not found"));
        var group = groupRepository.findById(removeGroupMemberDto.getId()).
                orElseThrow(() -> new GroupNotFoundException("Remove member: Group not found!"));

        if (user.equals(group.getOwner()) || user.equals(removedMember)) {
            group.removeMember(removedMember);
            var cartsOfRemovedMember = cartRepository.findCartsByGroupAndUser(group, removedMember);
            if (cartsOfRemovedMember.size() > 0) cartRepository.deleteAll(cartsOfRemovedMember);
        } else throw new NotOwnerOfGroupException("Remove member: You are not allowed to remove other members from the group");

        return new GroupMembersDto(groupRepository.save(group));
    }

    public void deleteGroup(Long id) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        var user = getCurrentUser();
        var group = groupRepository.findById(id).
                orElseThrow(() -> new GroupNotFoundException("Delete Group: Group not found"));
        var groupOwner = group.getOwner();

        if (groupOwner.equals(user)) {
            var membersToRemove = new HashSet<>(group.getMembers());
            group.removeAll(membersToRemove);
            if (group.getCarts().size() > 0) cartRepository.deleteAll(group.getCarts());
            if (group.getShoppingLists().size() > 0) {
                group.getShoppingLists().forEach(list -> shoppingItemRepository.deleteAll(list.getShoppingItems()));
                shoppingListRepository.deleteAll(group.getShoppingLists());
            }
            groupRepository.deleteById(id);
        } else throw new NotOwnerOfGroupException("Delete Group: You are not the owner of the group");
    }

    public void changeGroupOwner(ChangeGroupOwnerDto changeGroupOwnerDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        var user = getCurrentUser();
        var group = groupRepository.findById(changeGroupOwnerDto.getGroupId()).
                orElseThrow(() -> new GroupNotFoundException("Change Group Owner: Group not found"));
        var groupOwner = group.getOwner();
        var newGroupOwner = userRepository.findById(changeGroupOwnerDto.getNewOwner().getId()).
                orElseThrow(() -> new UserNotFoundException("Change Group Owner: User not found"));

        if (groupOwner.equals(user)) {
            group.removeMember(newGroupOwner);
            group.setOwner(newGroupOwner);
            group.addMember(user);
            groupRepository.save(group);
        } else throw new NotOwnerOfGroupException("Change Group Owner: You are not the owner of the group");
    }

    // TODO: Derzeit angemeldete Nutzer -> Spring Security
    private User getCurrentUser() throws UserNotFoundException {
        // Sven als Nutzer, id = 1
        var userId = UserEnum.CURRENT_USER.getId();
        return userRepository.findById(userId).
                orElseThrow(() -> new UserNotFoundException("User with id " + userId + " not found"));
    }

}
