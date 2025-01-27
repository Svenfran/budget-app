package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.entity.*;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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


    public DataLoaderService(
            CategoryRepository categoryRepository,
            CartRepository cartRepository,
            UserRepository userRepository,
            GroupRepository groupRepository,
            ShoppingListRepository shoppingListRepository,
            ShoppingItemRepository shoppingItemRepository){
        this.categoryRepository = categoryRepository;
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.shoppingListRepository = shoppingListRepository;
        this.shoppingItemRepository = shoppingItemRepository;
    }


    public Group loadGroup(Long groupId) throws GroupNotFoundException {
        return groupRepository.findById(groupId)
                        .orElseThrow(() -> new GroupNotFoundException(String.format("Group with Id %s not found", groupId)));
    }

    public Cart loadCart(Long cartId) throws CartNotFoundException {
        return cartRepository.findById(cartId)
                        .orElseThrow(() -> new CartNotFoundException(String.format("Cart with Id %s not found", cartId)));
    }

    public List<Cart> loadCartListForGroup(Long groupId) {
        return cartRepository.findCartsByGroupIdAndIsDeletedFalseOrderByDatePurchasedDesc(groupId);
    }

    public Category loadCategory(Long categoryId) throws CategoryNotFoundException {
        return categoryRepository.findById(categoryId)
                        .orElseThrow(() -> new CategoryNotFoundException(String.format("Category with Id %s not found", categoryId)));
    }

    public List<Category> loadCategoriesForGroup(Long groupId) {
        return categoryRepository.findAllByGroup_IdOrderByName(groupId);
    }

    public Category loadCategoryByGroupAndName(Group group, String categoryName) {
        return categoryRepository.findCategoryByGroupAndName(group, categoryName);
    }

    public User loadUser(Long userId) throws UserNotFoundException {
        return userRepository.findById(userId)
                        .orElseThrow(() -> new UserNotFoundException(String.format("User with Id %s not found", userId)));
    }

    public User loadUserByEmail(String email) throws UserNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(String.format("User with email %s not found", email)));
    }

    public int getMemberCountForCartByDatePurchasedAndGroup(Date datePurchased, Long groupId) {
        return cartRepository.getGroupMemberCountForCartDatePurchased(datePurchased, groupId);
    }

    public ShoppingList loadShoppingList(Long listId) throws ShoppingListNotFoundException {
        return shoppingListRepository.findById(listId)
                        .orElseThrow(() -> new ShoppingListNotFoundException(String.format("Shoppinglist with Id %s not found", listId)));
    }

    public ShoppingItem loadShoppingItem(Long itemId) throws ShoppingItemNotFoundException {
        return shoppingItemRepository.findById(itemId)
                        .orElseThrow(() -> new ShoppingItemNotFoundException(String.format("Shoppingitem with Id %s not found", itemId)));
    }

    public List<GroupMembershipHistory> loadMembershipHistoryForGroup(Long groupId) {
        return groupMembershipHistoryRepository.findByGroupId(groupId);
    }

    public List<GroupMembershipHistory> loadMembershipHistoryForGroupAndUser(Long groupId, Long userId) {
        return groupMembershipHistoryRepository.findByGroupIdAndUserId(groupId, userId);
    }

    public User getAuthenticatedUser() throws UserNotFoundException {
        UserDetails userDetails = (UserDetails) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException(String.format("User with email %s not found", userDetails.getUsername())));
    }

}
