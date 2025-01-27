package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.BaseClass;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class DataLoaderServiceExceptionsTest extends BaseClass {

    @Mock private CategoryRepository categoryRepository;
    @Mock private CartRepository cartRepository;
    @Mock private UserRepository userRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private ShoppingListRepository shoppingListRepository;
    @Mock private ShoppingItemRepository shoppingItemRepository;

    private DataLoaderService dataLoaderService;

    private static final Long GROUP_ID = 9L;
    private static final Long USER_ID = 99L;
    private static final Long CART_ID = 999L;
    private static final Long CATEGORY_ID = 9999L;
    private static final Long LIST_ID = 99999L;
    private static final Long ITEM_ID = 999999L;
    private static final String EMAIL = "user@example.com";

    DataLoaderServiceExceptionsTest() {
    }

    @BeforeEach
    void setUp() {
        dataLoaderService = new DataLoaderService(
                categoryRepository,
                cartRepository,
                userRepository,
                groupRepository,
                shoppingListRepository,
                shoppingItemRepository
        );
    }

    // Exceptions
    @Test
    void testThrowGroupNotFoundExceptionLoadGroupById() {
        Exception exception = assertThrows(GroupNotFoundException.class, () -> {
            dataLoaderService.loadGroup(GROUP_ID);
        }, "Expected GroupNotFoundException");
        logger.info(exception.getMessage());
        assertEquals(String.format("Group with Id %s not found", GROUP_ID), exception.getMessage());
    }

    @Test
    void testThrowUserNotFoundExceptionLoadUserByEmail() {
        Exception exception = assertThrows(UserNotFoundException.class, () -> {
            dataLoaderService.loadUserByEmail(EMAIL);
        }, "Expected UserNotFoundException");
        logger.info(exception.getMessage());
        assertEquals(String.format("User with email %s not found", EMAIL), exception.getMessage());
    }

    @Test
    void testThrowCartNotFoundExceptionLoadCartById() {
        Exception exception = assertThrows(CartNotFoundException.class, () -> {
            dataLoaderService.loadCart(CART_ID);
        }, "Expected CartNotFoundException");
        logger.info(exception.getMessage());
        assertEquals(String.format("Cart with Id %s not found", CART_ID), exception.getMessage());
    }

    @Test
    void testThrowCategoryNotFoundExceptionLoadCategoryById() {
        Exception exception = assertThrows(CategoryNotFoundException.class, () -> {
            dataLoaderService.loadCategory(CATEGORY_ID);
        }, "Expected CategoryNotFoundException");
        logger.info(exception.getMessage());
        assertEquals(String.format("Category with Id %s not found", CATEGORY_ID), exception.getMessage());
    }

    @Test
    void testThrowUserNotFoundExceptionLoadUserById() {
        Exception exception = assertThrows(UserNotFoundException.class, () -> {
            dataLoaderService.loadUser(USER_ID);
        }, "Expected UserNotFoundException");
        logger.info(exception.getMessage());
        assertEquals(String.format("User with Id %s not found", USER_ID), exception.getMessage());
    }

    @Test
    void testThrowShoppingListNotFoundExceptionLoadListById() {
        Exception exception = assertThrows(ShoppingListNotFoundException.class, () -> {
            dataLoaderService.loadShoppingList(LIST_ID);
        }, "Expected ShoppingListNotFoundException");
        logger.info(exception.getMessage());
        assertEquals(String.format("Shoppinglist with Id %s not found", LIST_ID), exception.getMessage());
    }

    @Test
    void testThrowShoppingItemNotFoundExceptionLoadItemById() {
        Exception exception = assertThrows(ShoppingItemNotFoundException.class, () -> {
            dataLoaderService.loadShoppingItem(ITEM_ID);
        }, "Expected ShoppingItemNotFoundException");
        logger.info(exception.getMessage());
        assertEquals(String.format("Shoppingitem with Id %s not found", ITEM_ID), exception.getMessage());
    }
}
