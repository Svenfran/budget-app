package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.constants.UserEnum;
import com.github.svenfran.budgetapp.budgetappbackend.dto.*;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.repository.*;
import com.github.svenfran.budgetapp.budgetappbackend.service.mapper.GroupDtoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

@Service
public class GroupService {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupDtoMapper groupDtoMapper;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ShoppingListRepository shoppingListRepository;

    @Autowired
    private ShoppingItemRepository shoppingItemRepository;

    @Autowired
    private GroupMembershipHistoryService groupMembershipHistoryService;

    @Autowired
    private GroupMembershipHistoryRepository groupMembershipHistoryRepository;

    @Autowired
    private DataLoaderService dataLoaderService;


    public Stream<Group> getGroupsByMemberOrOwner() throws UserNotFoundException {
        var user = dataLoaderService.getCurrentUser();
        var member = new HashSet<User>();
        member.add(user);
        var groupsMember = groupRepository.findGroupsByMembersInOrderById(member);
        var groupsOwner = groupRepository.findGroupsByOwnerOrderById(user);
        return Stream.concat(groupsOwner.stream(), groupsMember.stream()).sorted((a,b) -> (int) (a.getId() - b.getId()));
    }

    public List<GroupSideNavDto> getGroupsForSideNav() throws UserNotFoundException {
        return getGroupsByMemberOrOwner().map(GroupSideNavDto::new).toList();
    }

    public List<GroupOverviewDto> getGroupsForGroupOverview() throws UserNotFoundException {
        return getGroupsByMemberOrOwner().map(GroupOverviewDto::new).toList();
    }

    public GroupMembersDto getGroupMembers(Long groupId) throws UserNotFoundException, GroupNotFoundException, NotOwnerOrMemberOfGroupException {
        var user = dataLoaderService.getCurrentUser();
        var group = dataLoaderService.loadGroup(groupId);
        verifyIsPartOfGroup(user, group);
        return new GroupMembersDto(group);
    }

    @Transactional
    public GroupDto addGroup(GroupDto groupDto) throws UserNotFoundException {
        var user = dataLoaderService.getCurrentUser();
        var group = groupRepository.save(groupDtoMapper.GroupDtoToEntity(groupDto, user));
        createDefaultCategories(group);
        groupMembershipHistoryService.startGroupMembershipForOwner(user, group);
        return new GroupDto(group);
    }

    public GroupDto updateGroup(GroupDto groupDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        var user = dataLoaderService.getCurrentUser();
        var group = dataLoaderService.loadGroup(groupDto.getId());
        verifyIsGroupOwner(user, group);
        var groupOwner = dataLoaderService.loadUser(group.getOwner().getId());
        return new GroupDto(groupRepository.save(groupDtoMapper.GroupDtoToEntity(groupDto, groupOwner)));
    }

    @Transactional
    public GroupMembersDto addMemberToGroup(AddGroupMemberDto addGroupMemberDto) throws GroupNotFoundException, UserNotFoundException, NotOwnerOfGroupException, MemberAlreadyExixtsException, MemberEqualsOwnerException {
        var user = dataLoaderService.getCurrentUser();
        var newMember = dataLoaderService.loadUserByEmail(addGroupMemberDto.getNewMemberEmail());
        verifyUserExists(newMember);
        var group = dataLoaderService.loadGroup(addGroupMemberDto.getId());
        verifyIsGroupOwner(user, group);
        verifyCurrentlyNoGroupMember(newMember, group);
        verifyMemberNotGroupOwner(newMember, user);

        group.addMember(newMember);
        groupMembershipHistoryService.startGroupMembershipForMember(newMember, group);

        var cartsOfNewMember = cartRepository.findCartsByGroupAndUser(group, newMember);
        if (cartsOfNewMember.size() > 0) {
            cartsOfNewMember.forEach(cart -> cart.setDeleted(false));
            cartRepository.saveAll(cartsOfNewMember);
        }
        var cartsOfGroup = group.getCarts();
        if (cartsOfGroup.size() > 0) {
            cartsOfGroup.forEach(cart -> cart.setAveragePerMember(cart.getAmount() / dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(cart.getDatePurchased(), cart.getGroup().getId())));
            cartRepository.saveAll(cartsOfGroup);
        }
        return new GroupMembersDto(groupRepository.save(group));
    }

    @Transactional
    public GroupMembersDto removeMemberFromGroup(RemoveGroupMemberDto removeGroupMemberDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        var user = dataLoaderService.getCurrentUser();
        var removedMember = dataLoaderService.loadUser(removeGroupMemberDto.getMember().getId());
        var group = dataLoaderService.loadGroup(removeGroupMemberDto.getId());
        verifyIsOwnerOrMemberToRemove(user, removedMember, group);

        group.removeMember(removedMember);
        groupMembershipHistoryService.finishGroupMembership(removedMember, group);
        var cartsOfRemovedMember = cartRepository.findCartsByGroupAndUser(group, removedMember);
        if (cartsOfRemovedMember.size() > 0) {
            cartsOfRemovedMember.forEach(cart -> cart.setDeleted(true));
            cartRepository.saveAll(cartsOfRemovedMember);
        }
        var cartsOfGroup = group.getCarts();
        if (cartsOfGroup.size() > 0) {
            cartsOfGroup.forEach(cart -> cart.setAveragePerMember(cart.getAmount() / dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(cart.getDatePurchased(), cart.getGroup().getId())));
            cartRepository.saveAll(cartsOfGroup);
        }

        return new GroupMembersDto(groupRepository.save(group));
    }

    @Transactional
    public void deleteGroup(Long id) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        var user = dataLoaderService.getCurrentUser();
        var group = dataLoaderService.loadGroup(id);
        var groupMembershipToRemove = dataLoaderService.loadMembershipHistory(group.getId());
        verifyIsGroupOwner(user, group);

        var membersToRemove = new HashSet<>(group.getMembers());
        group.removeAll(membersToRemove);
        groupMembershipHistoryRepository.deleteAll(groupMembershipToRemove);
        if (group.getCarts().size() > 0) cartRepository.deleteAll(group.getCarts());
        if (group.getCategories().size() > 0) categoryRepository.deleteAll(group.getCategories());
        if (group.getShoppingLists().size() > 0) {
            group.getShoppingLists().forEach(list -> shoppingItemRepository.deleteAll(list.getShoppingItems()));
            shoppingListRepository.deleteAll(group.getShoppingLists());
        }
        groupRepository.deleteById(id);
    }

    @Transactional
    public void changeGroupOwner(ChangeGroupOwnerDto changeGroupOwnerDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        var user = dataLoaderService.getCurrentUser();
        var group = dataLoaderService.loadGroup(changeGroupOwnerDto.getGroupId());
        var newGroupOwner = dataLoaderService.loadUser(changeGroupOwnerDto.getNewOwner().getId());
        verifyIsGroupOwner(user, group);

        group.removeMember(newGroupOwner);
        group.setOwner(newGroupOwner);
        group.addMember(user);
        groupMembershipHistoryService.changeGroupOwnerToMember(group.getOwner(), group);
        groupMembershipHistoryService.changeGroupMemberToOwner(newGroupOwner, group);
        groupRepository.save(group);
    }

    private void createDefaultCategories(Group group) {
        categoryRepository.save(new Category(null, "Ausgehen", group, null));
        categoryRepository.save(new Category(null, "Ausgleichszahlung", group, null));
        categoryRepository.save(new Category(null, "Geschenke", group, null));
        categoryRepository.save(new Category(null, "Lebensmittel", group, null));
        categoryRepository.save(new Category(null, "Restaurant", group, null));
        categoryRepository.save(new Category(null, "Wohnung", group, null));
        categoryRepository.save(new Category(null, "Sonstiges", group, null));
    }

    private void verifyIsPartOfGroup(User user, Group group) throws NotOwnerOrMemberOfGroupException {
        if (!(group.getOwner().equals(user) || group.getMembers().contains(user))) {
            throw new NotOwnerOrMemberOfGroupException("User with ID " + user.getId() + " is either a member nor the owner of the group");
        }
    }

    private void verifyIsGroupOwner(User user, Group group) throws NotOwnerOfGroupException {
        if (!group.getOwner().equals(user)) {
            throw new NotOwnerOfGroupException("User with Id " + user.getId() + " is not the owner of the Group");
        }
    }

    private void verifyUserExists(User user) throws UserNotFoundException {
        if (user == null) throw new UserNotFoundException("User not found");
    }

    private void verifyCurrentlyNoGroupMember(User user, Group group) throws MemberAlreadyExixtsException {
        if (group.getMembers().contains(user)) {
            throw new MemberAlreadyExixtsException("Member already exists");
        }
    }

    private void verifyMemberNotGroupOwner(User newMember, User user) throws MemberEqualsOwnerException {
        if (newMember.equals(user)) {
            throw new MemberEqualsOwnerException("New member equals group owner");
        }
    }

    private void verifyIsOwnerOrMemberToRemove(User user, User removedMember, Group group) throws NotOwnerOfGroupException {
        if (!(user.equals(group.getOwner()) || user.equals(removedMember))) {
            throw new NotOwnerOfGroupException("User with Id " + user.getId() + " not allowed to remove other members from the group");
        }
    }
}
