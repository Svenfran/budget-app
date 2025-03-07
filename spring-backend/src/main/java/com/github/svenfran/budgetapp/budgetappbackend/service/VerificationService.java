package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.*;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class VerificationService {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    public void verifyIsAuthenticatedUser(User user, User authUser) throws UserIsNotAuthenticatedUser {
        if (!user.equals(authUser)) {
            throw new UserIsNotAuthenticatedUser("User to edit or delete is not authenticated user");
        }
    }

    public void verifyIsCorrectPassword(User user, String password) throws WrongPasswordException {
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new WrongPasswordException("Incorrect password");
        }
    }

    public void verifyEmailNotExists(String email) throws UserAlreadyExistException {
        if (emailExists(email)) {
            throw new UserAlreadyExistException(String.format("User with email %s already exists", email));
        }
    }

    public void verifyEmailIsValid(BindingResult bindingResult) throws InvalidEmailException {
        if (bindingResult.hasErrors()) {
            throw new InvalidEmailException("Invalid Email");
        }
    }

    public void verifyUserNameNotExists(String userName) throws UserNameAlreadyExistsException {
        if (userNameExists(userName)) {
            throw new UserNameAlreadyExistsException(String.format("User with name %s already exists", userName));
        }
    }

    public void verifyIsPartOfGroup(User user, Group group) throws NotOwnerOrMemberOfGroupException {
        if (!(group.getOwner().equals(user) || group.getMembers().contains(user))) {
            throw new NotOwnerOrMemberOfGroupException("User with ID " + user.getId() + " is either a member nor the owner of the group");
        }
    }

    public void verifyIsOwnerOfCart(User user, Cart cart) throws NotOwnerOfCartException {
        if (!cart.getUser().equals(user)) {
            throw new NotOwnerOfCartException("User with ID " + user.getId() + " is not the owner of the cart");
        }
    }

    public void verifyCategoryIsPartOfGroup(Category category, Group group) throws CategoryBelongsNotToGroupException {
        if (!group.getId().equals(category.getGroup().getId())) {
            throw new CategoryBelongsNotToGroupException("Category with Id " + category.getId() + " does not belong to group with Id " + group.getId());
        }
    }

    public void verifyCategoryNotInUse(Category category) throws CategoryIsUsedByCartException {
        if (!category.getCarts().isEmpty()) {
            throw new CategoryIsUsedByCartException("Category with Id " + category.getId() + " is used by carts");
        }
    }

    public void verifyIsGroupOwner(User user, Group group) throws NotOwnerOfGroupException {
        if (!group.getOwner().equals(user)) {
            throw new NotOwnerOfGroupException("User with Id " + user.getId() + " is not the owner of the Group");
        }
    }

    public void verifyUserExists(User user) throws UserNotFoundException {
        if (user == null) throw new UserNotFoundException("User not found");
    }

    public void verifyCurrentlyNoGroupMember(User user, Group group) throws MemberAlreadyExixtsException {
        if (group.getMembers().contains(user)) {
            throw new MemberAlreadyExixtsException("Member already exists");
        }
    }

    public void verifyMemberNotGroupOwner(User newMember, User user) throws MemberEqualsOwnerException {
        if (newMember.equals(user)) {
            throw new MemberEqualsOwnerException("New member equals group owner");
        }
    }

    public void verifyIsOwnerOrMemberToRemove(User user, User removedMember, Group group) throws NotOwnerOfGroupException {
        if (!(user.equals(group.getOwner()) || user.equals(removedMember))) {
            throw new NotOwnerOfGroupException("User with Id " + user.getId() + " not allowed to remove other members from the group");
        }
    }

    public void verifyShoppingListIsPartOfGroup(ShoppingList shoppingList, Group group) throws ShoppingListDoesNotBelongToGroupException {
        if (!shoppingList.getGroup().equals(group)) {
            throw new ShoppingListDoesNotBelongToGroupException("Shoppinglist with Id " + shoppingList.getId() + " does not belong to group with Id " + group.getId());
        }
    }

    public void verifyShoppingItemIsPartOfShoppingList(ShoppingList shoppingList, ShoppingItem shoppingItem) throws ShoppingItemDoesNotBelongToShoppingListException {
        if (!shoppingList.getId().equals(shoppingItem.getShoppingList().getId())) {
            throw new ShoppingItemDoesNotBelongToShoppingListException("Shoppingitem with Id " + shoppingItem.getId() + " does not belong to shoppinglist with Id " + shoppingList.getId());
        }
    }

    public void verifyAllShoppingItemsBelongToSameShoppingListAndGroup(List<AddEditShoppingItemDto> shoppingItems) {
        if (shoppingItems.stream()
                .map(shoppingItem -> Map.entry(shoppingItem.getGroupId(), shoppingItem.getShoppingListId()))
                .distinct()
                .count() > 1) {
            throw new IllegalArgumentException("One of the shoppingitems does not belong to the shoppinglist or group ");
        }
    }

    public void verifyDatePurchasedWithinMembershipPeriod (List<GroupMembershipHistory> gmh, Date datePurchased) throws DatePurchasedNotWithinMembershipPeriodException {
        for (var timePeriod : gmh) {
            var startDate = timePeriod.getMembershipStart().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            var endDate = timePeriod.getMembershipEnd() != null ? timePeriod.getMembershipEnd().toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : null;

            var isAfterStart = !datePurchased.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().isBefore(startDate);
            var isBeforeEnd = (endDate == null) || !datePurchased.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().isAfter(endDate);

            if (isAfterStart && isBeforeEnd) {
                return;
            }
        } throw new DatePurchasedNotWithinMembershipPeriodException("Date purchased is not within membership period");
    }

    private boolean emailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    private boolean userNameExists(String userNAme) {
        return userRepository.findByName(userNAme).isPresent();
    }

}
