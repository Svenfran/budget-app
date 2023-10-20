package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.RemoveGroupMemberDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;

@Service
public class UserProfileService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataLoaderService dataLoaderService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ShoppingListRepository shoppingListRepository;

    @Autowired
    private ShoppingItemRepository shoppingItemRepository;

    @Autowired
    private GroupMembershipHistoryService gmhService;

    @Autowired
    private CartService cartService;

    @Autowired
    private AuthenticationService authenticationService;


    @Transactional
    public void deleteUserProfile(Long userId) throws UserNotFoundException, UserIsNotAuthenticatedUser {
        var userAuth = dataLoaderService.getAuthenticatedUser();
        var userDelete = dataLoaderService.loadUser(userId);
        verifyIsAuthenticatedUser(userDelete, userAuth);
        var allUserGroups = userDelete.getGroupList();
        for (Group group : allUserGroups) {
            if (group.getOwner().equals(userDelete)) {
                var groupMembershipToRemove = dataLoaderService.loadMembershipHistory(group.getId());
                if (!group.getMembers().isEmpty()) group.removeAllMembers();
                if (!groupMembershipToRemove.isEmpty()) groupMembershipToRemove.forEach(gmh -> gmh.setGroupId(null));
                if (!group.getCarts().isEmpty()) cartRepository.deleteAll(group.getCarts());
                if (!group.getCategories().isEmpty()) categoryRepository.deleteAll(group.getCategories());
                if (!group.getShoppingLists().isEmpty()) {
                    group.getShoppingLists().forEach(list -> shoppingItemRepository.deleteAll(list.getShoppingItems()));
                    shoppingListRepository.deleteAll(group.getShoppingLists());
                }
                groupRepository.deleteById(group.getId());
            }

            if (group.getMembers().contains(userDelete)) {
                var removeGroupMember = new RemoveGroupMemberDto();
                removeGroupMember.setId(group.getId());
                removeGroupMember.setMember(new UserDto(userDelete));
                group.removeMember(userDelete);
                gmhService.finishGroupMembership(userDelete, group);
                groupService.setIsDeletedForCart(group, userDelete, true);
                groupService.calculateAveragePerMember(group);
            }
        }
        cartService.deleteCartsForUserWhereIsDeletedTrue(userDelete);
        userRepository.deleteById(userDelete.getId());
        gmhService.deleteGroupMembershipHistoryWhereGroupIdIsNull();

    }

    @Transactional
    public UserDto changeUserName(UserDto userDto) throws UserNotFoundException, UserIsNotAuthenticatedUser, UserNameAlreadyExistsException {
        var userAuth = dataLoaderService.getAuthenticatedUser();
        var userChange = dataLoaderService.loadUser(userDto.getId());
        verifyIsAuthenticatedUser(userChange, userAuth);
        authenticationService.verifyUserNameNotExists(userDto.getUserName());
        userChange.setName(userDto.getUserName());
        return new UserDto(userRepository.save(userChange), userChange.getEmail());
    }

    @Transactional
    public UserDto changeUserEmail(UserDto userDto, BindingResult bindingResult) throws UserNotFoundException, UserIsNotAuthenticatedUser, UserAlreadyExistException, InvalidEmailException {
        var userAuth = dataLoaderService.getAuthenticatedUser();
        var userChange = dataLoaderService.loadUser(userDto.getId());
        verifyIsAuthenticatedUser(userChange, userAuth);
        authenticationService.verifyEmailIsValid(bindingResult);
        authenticationService.verifyEmailNotExists(userDto.getUserEmail());
        userChange.setEmail(userDto.getUserEmail());
        return new UserDto(userRepository.save(userChange), userDto.getUserEmail());
    }

    private void verifyIsAuthenticatedUser(User user, User authUser) throws UserIsNotAuthenticatedUser {
        if (!user.equals(authUser)) {
            throw new UserIsNotAuthenticatedUser("User to edit or delete is not authenticated user");
        }
    }

}
