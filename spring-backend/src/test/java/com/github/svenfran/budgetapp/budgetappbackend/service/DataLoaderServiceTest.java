package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.entity.Group;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.CategoryNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.GroupNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DataLoaderServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private CartRepository cartRepository;
    @Mock private UserRepository userRepository;
    @Mock private GroupMembershipHistoryRepository gmhRepository;

    private DataLoaderService dataLoaderService;

    private static final Long GROUP_ID = 10L;
    private static final Group GROUP = new Group();
    private static final String CATEGORY_NAME = "TestCategory";
    private static final Date DATE_PURCHASED = new Date();
    private static final String EMAIL = "user@example.com";

    @BeforeEach
    void setUp() {
        dataLoaderService = new DataLoaderService(
                categoryRepository,
                cartRepository,
                userRepository,
                gmhRepository
        );
    }


    // Category
    @Test
    void loadCategoriesForGroup() {
        dataLoaderService.loadCategoriesForGroup(GROUP_ID);
        verify(categoryRepository).findAllByGroup_IdOrderByName(GROUP_ID);
    }

    @Test
    void loadCategoryByGroupAndName() {
        dataLoaderService.loadCategoryByGroupAndName(GROUP, CATEGORY_NAME);
        verify(categoryRepository).findCategoryByGroupAndName(GROUP, CATEGORY_NAME);
    }

    // Cart
    @Test
    void loadCartListForGroup() {
        dataLoaderService.loadCartListForGroup(GROUP_ID);
        verify(cartRepository).findCartsByGroupIdAndIsDeletedFalseOrderByDatePurchasedDesc(GROUP_ID);
    }

    @Test
    void getMemberCountForCartByDatePurchasedAndGroup() {
        dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(DATE_PURCHASED, GROUP_ID);
        verify(cartRepository).getGroupMemberCountForCartDatePurchased(DATE_PURCHASED, GROUP_ID);
    }

    // User
    @Test
    void loadUserByEmail() {
        dataLoaderService.loadUserByEmail(EMAIL);
        verify(userRepository).findByEmail(EMAIL);
    }

    // GroupMembershipHistory
    @Test
    void loadMembershipHistory() {
        dataLoaderService.loadMembershipHistory(GROUP_ID);
        verify(gmhRepository).findByGroupId(GROUP_ID);
    }

}
