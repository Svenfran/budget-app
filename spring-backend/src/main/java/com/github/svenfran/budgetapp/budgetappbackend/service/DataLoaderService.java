package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.constants.UserEnum;
import com.github.svenfran.budgetapp.budgetappbackend.entity.*;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class DataLoaderService {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShoppingListRepository shoppingListRepository;

    @Autowired
    private ShoppingItemRepository shoppingItemRepository;

    @Autowired
    private GroupMembershipHistoryRepository groupMembershipHistoryRepository;


    public Group loadGroup(Long groupId) throws GroupNotFoundException {
        return groupRepository.findById(groupId).
                orElseThrow(() -> new GroupNotFoundException("Group wit Id " + groupId + " not found"));
    }

    public Cart loadCart(Long cartId) throws CartNotFoundException {
        return cartRepository.findById(cartId).
                orElseThrow(() -> new CartNotFoundException("Cart with Id " + cartId + " not found"));
    }

    public List<Cart> loadCartListForGroup(Long groupId) {
        return cartRepository.findCartsByGroupIdAndIsDeletedFalseOrderByDatePurchasedDesc(groupId);
    }

    public Category loadCategory(Long categoryId) throws CategoryNotFoundException {
        return categoryRepository.findById(categoryId).
                orElseThrow(() -> new CategoryNotFoundException("Category with Id " + categoryId + " not found"));
    }

    public List<Category> loadCategoriesForGroup(Long groupId) {
        return categoryRepository.findAllByGroup_IdOrderByName(groupId);
    }

    public Category loadCategoryByGroupAndName(Group group, String categoryName) {
        return categoryRepository.findCategoryByGroupAndName(group, categoryName);
    }

    public User loadUser(Long userId) throws UserNotFoundException {
        return userRepository.findById(userId).
                orElseThrow(() -> new UserNotFoundException("User with Id " + userId + " not found"));
    }

    public User loadUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public int getMemberCountForCartByDatePurchasedAndGroup(Date datePurchased, Long groupId) {
        return cartRepository.getGroupMemberCountForCartDatePurchased(datePurchased, groupId);
    }

    public ShoppingList loadShoppingList(Long listId) throws ShoppingListNotFoundException {
        return shoppingListRepository.findById(listId).
                orElseThrow(() -> new ShoppingListNotFoundException("Shoppinglist with Id " + listId + " not found"));
    }

    public ShoppingItem loadShoppingItem(Long itemId) throws ShoppingItemNotFoundException {
        return shoppingItemRepository.findById(itemId).
                orElseThrow(() -> new ShoppingItemNotFoundException("Shoppingitem with Id " + itemId + " not found"));
    }

    public List<GroupMembershipHistory> loadMembershipHistory(Long groupId) {
        return groupMembershipHistoryRepository.findByGroupId(groupId);
    }

    // TODO: Derzeit angemeldete Nutzer -> Spring Security
    public User getCurrentUser() throws UserNotFoundException {
        var userId = UserEnum.SVEN.getId();
        return userRepository.findById(userId).
                orElseThrow(() -> new UserNotFoundException("User with Id " + userId + " not found"));
    }

}