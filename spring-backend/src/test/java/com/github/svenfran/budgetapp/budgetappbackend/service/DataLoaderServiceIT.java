package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.CreateDataService;
import com.github.svenfran.budgetapp.budgetappbackend.container.TestContainerEnv;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.CartNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.GroupNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataLoaderServiceIT extends TestContainerEnv {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupMembershipHistoryRepository gmhRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private DataLoaderService dataLoaderService;

    @Autowired
    private CreateDataService createDataService;

    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

    @BeforeEach
    void cleanUp() {
        groupRepository.deleteAll();
        categoryRepository.deleteAll();
        cartRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @Transactional
    void testLoadGroupById() throws GroupNotFoundException {
        var qmUser = createDataService.createUser("QM-User");
        var testUser = createDataService.createUser("Test-User");
        var qmGroup = createDataService.createGroup("QM-Group", qmUser);
        var testGroup = createDataService.createGroup("Test-Group", testUser);

        var loadQMGroup = dataLoaderService.loadGroup(qmGroup.getId());
        var loadTestGroup = dataLoaderService.loadGroup(testGroup.getId());

        logger.info(loadQMGroup.toString());
        logger.info(loadTestGroup.toString());

        assertEquals("QM-Group", loadQMGroup.getName());
        assertEquals("QM-User", loadQMGroup.getOwner().getName());

        assertEquals("Test-Group", loadTestGroup.getName());
        assertEquals("Test-User", loadTestGroup.getOwner().getName());
    }


    @Test
    @Transactional
    void testLoadCartById() throws CartNotFoundException, ParseException {
        var user = createDataService.createUser("Sven");
        var group = createDataService.createGroup("Test-Group", user);
        var category = createDataService.createCategory("Restaurant", group);
        var cart = createDataService.createCart("Test-Cart", 125.0, group, user, category, false, "2023-11-16", 1);

        var loadedCart = dataLoaderService.loadCart(cart.getId());
        logger.info(loadedCart.toString());
        assertEquals("Test-Cart", loadedCart.getTitle());
        assertEquals("Test-Group", loadedCart.getGroup().getName());
        assertEquals("Sven", loadedCart.getGroup().getOwner().getName());
        assertEquals("Restaurant", loadedCart.getCategory().getName());
        assertEquals(125.0, loadedCart.getAmount());
    }

    @Test
    @Transactional
    void testLoadCartListForGroup() throws ParseException {
        var user = createDataService.createUser("Test-User");
        var testGroup = createDataService.createGroup("Test-Group", user);
        var newTestGroup = createDataService.createGroup("New-Test-Group", user);
        var testCategory = createDataService.createCategory("Test-Category", testGroup);
        var newCategory = createDataService.createCategory("New-Category", testGroup);
        var newTestCategory = createDataService.createCategory("New-Test-Category", newTestGroup);
        var testCart = createDataService.createCart("Test-Cart", 50.0, testGroup, user, testCategory, false, "2023-11-10", 1);
        var newCart = createDataService.createCart("New-Cart", 125.0, testGroup, user, newCategory, false, "2023-11-15", 1);
        var newTestCart = createDataService.createCart("New-Test-Cart", 15.0, newTestGroup, user, newTestCategory, false, "2023-11-17", 1);

        var cartListTest = dataLoaderService.loadCartListForGroup(testGroup.getId());
        var cartListQM = dataLoaderService.loadCartListForGroup(newTestGroup.getId());

        logger.info(testCart.toString());
        logger.info(newCart.toString());
        logger.info(newTestCart.toString());

        assertEquals(2, cartListTest.size());
        assertEquals(1, cartListQM.size());

        assertEquals("Test-Cart", testCart.getTitle());
        assertEquals("Test-Group", testCart.getGroup().getName());
        assertEquals(50.0, testCart.getAmount());
        assertEquals("Test-Category", testCart.getCategory().getName());
        assertEquals(formatter.parse("2023-11-10").toString(), testCart.getDatePurchased().toString());

        assertEquals("New-Cart", newCart.getTitle());
        assertEquals("Test-Group", newCart.getGroup().getName());
        assertEquals(125.0, newCart.getAmount());
        assertEquals("New-Category", newCart.getCategory().getName());
        assertEquals(formatter.parse("2023-11-15").toString(), newCart.getDatePurchased().toString());

        assertEquals("New-Test-Cart", newTestCart.getTitle());
        assertEquals("New-Test-Group", newTestCart.getGroup().getName());
        assertEquals(15.0, newTestCart.getAmount());
        assertEquals("New-Test-Category", newTestCart.getCategory().getName());
        assertEquals(formatter.parse("2023-11-17").toString(), newTestCart.getDatePurchased().toString());
    }

    @Test
    @Transactional
    void loadCategoriesForGroup() {
        var user = createDataService.createUser("Test-User");
        var groupTest = createDataService.createGroup("Test-Group", user);
        var groupQM = createDataService.createGroup("QM-Group", user);
        createDataService.createCategory("Lebensmittel", groupTest);
        createDataService.createCategory("Restaurant", groupTest);
        createDataService.createCategory("Miete", groupTest);
        createDataService.createCategory("Ausgehen", groupQM);
        createDataService.createCategory("Sport", groupQM);

        var categoriesTest = dataLoaderService.loadCategoriesForGroup(groupTest.getId());
        var categoriesQM = dataLoaderService.loadCategoriesForGroup(groupQM.getId());

        assertEquals(3, categoriesTest.size());
        assertEquals(2, categoriesQM.size());
    }

    @Test
    @Transactional
    void loadCategoryByGroupAndName() {
        var user = createDataService.createUser("Test-User");
        var groupTest = createDataService.createGroup("Test-Group", user);
        var groupQM = createDataService.createGroup("QM-Group", user);
        createDataService.createCategory("Lebensmittel", groupTest);
        createDataService.createCategory("Restaurant", groupTest);
        createDataService.createCategory("Miete", groupTest);
        createDataService.createCategory("Ausgehen", groupQM);
        createDataService.createCategory("Sport", groupQM);

        assertEquals("Lebensmittel", dataLoaderService.loadCategoryByGroupAndName(groupTest, "Lebensmittel").getName());
        assertEquals("Restaurant", dataLoaderService.loadCategoryByGroupAndName(groupTest, "Restaurant").getName());
        assertEquals("Miete", dataLoaderService.loadCategoryByGroupAndName(groupTest, "Miete").getName());
        assertEquals("Ausgehen", dataLoaderService.loadCategoryByGroupAndName(groupQM, "Ausgehen").getName());
        assertEquals("Sport", dataLoaderService.loadCategoryByGroupAndName(groupQM, "Sport").getName());
    }

    @Test
    void getMemberCountForCartByDatePurchasedAndGroup() {
    }

    @Test
    void loadShoppingList() {
    }

    @Test
    void loadShoppingItem() {
    }

    @Test
    void loadMembershipHistory() {
    }

    @Test
    void getAuthenticatedUser() {
    }

}
