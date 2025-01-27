package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.*;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
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
    private GroupMembershipHistoryRepository gmhRepository;

    @Autowired
    private DataLoaderService dataLoaderService;

    @Autowired
    private VerificationService verificationService;


    public Stream<Group> getGroupsByMemberOrOwner() throws UserNotFoundException {
        var user = dataLoaderService.getAuthenticatedUser();
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
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(groupId);
        verificationService.verifyIsPartOfGroup(user, group);
        return new GroupMembersDto(group);
    }

    @Transactional
    public GroupDto addGroup(GroupDto groupDto) throws UserNotFoundException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = groupRepository.save(groupDtoMapper.GroupDtoToEntity(groupDto, user));
        createDefaultCategories(group);
        groupMembershipHistoryService.startGroupMembershipForOwner(user, group);
        return new GroupDto(group);
    }

    public GroupDto updateGroup(GroupDto groupDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(groupDto.getId());
        verificationService.verifyIsGroupOwner(user, group);
        var groupOwner = dataLoaderService.loadUser(group.getOwner().getId());
        return new GroupDto(groupRepository.save(groupDtoMapper.GroupDtoToEntity(groupDto, groupOwner)));
    }

    @Transactional
    public GroupMembersDto addMemberToGroup(AddGroupMemberDto addGroupMemberDto) throws GroupNotFoundException, UserNotFoundException, NotOwnerOfGroupException, MemberAlreadyExixtsException, MemberEqualsOwnerException {
        var user = dataLoaderService.getAuthenticatedUser();
        var newMember = dataLoaderService.loadUserByEmail(addGroupMemberDto.getNewMemberEmail().trim());
        verificationService.verifyUserExists(newMember);
        var group = dataLoaderService.loadGroup(addGroupMemberDto.getId());
        verificationService.verifyIsGroupOwner(user, group);
        verificationService.verifyCurrentlyNoGroupMember(newMember, group);
        verificationService.verifyMemberNotGroupOwner(newMember, user);

        group.addMember(newMember);
        groupMembershipHistoryService.startGroupMembershipForMember(newMember, group);
        setIsDeletedForCart(group, newMember, false);

        calculateAveragePerMember(group);
        return new GroupMembersDto(groupRepository.save(group));
    }

    @Transactional
    public GroupMembersDto removeMemberFromGroup(RemoveGroupMemberDto removeGroupMemberDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var removedMember = dataLoaderService.loadUser(removeGroupMemberDto.getMember().getId());
        var group = dataLoaderService.loadGroup(removeGroupMemberDto.getId());
        verificationService.verifyIsOwnerOrMemberToRemove(user, removedMember, group);

        group.removeMember(removedMember);
        groupMembershipHistoryService.finishGroupMembership(removedMember, group);
        setIsDeletedForCart(group, removedMember, true);

        calculateAveragePerMember(group);
        return new GroupMembersDto(groupRepository.save(group));
    }

    @Transactional
    public void deleteGroup(Long id) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(id);
        verificationService.verifyIsGroupOwner(user, group);
        var groupMembershipToRemove = dataLoaderService.loadMembershipHistoryForGroup(group.getId());
        if (!group.getMembers().isEmpty()) group.removeAllMembers();
        if (!groupMembershipToRemove.isEmpty()) gmhRepository.deleteAll(groupMembershipToRemove);
        if (!group.getCarts().isEmpty()) cartRepository.deleteAll(group.getCarts());
        if (!group.getCategories().isEmpty()) categoryRepository.deleteAll(group.getCategories());
        if (!group.getShoppingLists().isEmpty()) {
            group.getShoppingLists().forEach(list -> shoppingItemRepository.deleteAll(list.getShoppingItems()));
            shoppingListRepository.deleteAll(group.getShoppingLists());
        }
        groupRepository.deleteById(id);
    }

    @Transactional
    public void changeGroupOwner(ChangeGroupOwnerDto changeGroupOwnerDto) throws UserNotFoundException, GroupNotFoundException, NotOwnerOfGroupException {
        var user = dataLoaderService.getAuthenticatedUser();
        var group = dataLoaderService.loadGroup(changeGroupOwnerDto.getGroupId());
        var newGroupOwner = dataLoaderService.loadUser(changeGroupOwnerDto.getNewOwner().getId());
        verificationService.verifyIsGroupOwner(user, group);

        group.removeMember(newGroupOwner);
        group.setOwner(newGroupOwner);
        group.addMember(user);
        groupMembershipHistoryService.changeGroupOwnerToMember(user, group);
        groupMembershipHistoryService.changeGroupMemberToOwner(newGroupOwner, group);
        groupRepository.save(group);
    }

    private void createDefaultCategories(Group group) {
        var defaultCategories = new String[]{
                "Ausgehen", "Ausgleichszahlung", "Geschenke", "Lebensmittel",
                "Restaurant", "Wohnung", "Sonstiges"};
        for (String categoryName : defaultCategories) {
            var category = new Category();
            category.setName(categoryName);
            category.setGroup(group);
            categoryRepository.save(category);
        }
    }

    public void setIsDeletedForCart(Group group, User member, boolean delete) {
        var cartsOfMember = cartRepository.findCartsByGroupAndUser(group, member);
        if (delete && (!cartsOfMember.isEmpty())) {
            cartsOfMember.forEach(cart -> cart.setDeleted(true));
            cartRepository.saveAll(cartsOfMember);
        }
        if (!delete && (!cartsOfMember.isEmpty())) {
            cartsOfMember.forEach(cart -> cart.setDeleted(false));
            cartRepository.saveAll(cartsOfMember);
        }
    }

    public void calculateAveragePerMember(Group group) {
        var cartsOfGroup = group.getCarts();
        if (!cartsOfGroup.isEmpty()) {
            cartsOfGroup.forEach(cart -> cart.setAveragePerMember(cart.getAmount() / dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(cart.getDatePurchased(), cart.getGroup().getId())));
            cartRepository.saveAll(cartsOfGroup);
        }
    }
}
