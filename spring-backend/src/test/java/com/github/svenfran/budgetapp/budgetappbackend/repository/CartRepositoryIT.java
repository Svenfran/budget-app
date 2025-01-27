package com.github.svenfran.budgetapp.budgetappbackend.repository;

import com.github.svenfran.budgetapp.budgetappbackend.CreateDataService;
import com.github.svenfran.budgetapp.budgetappbackend.constants.TypeEnum;
import com.github.svenfran.budgetapp.budgetappbackend.container.TestContainerEnv;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CartRepositoryIT extends TestContainerEnv {

    @Autowired
    private CreateDataService createDataService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMembershipHistoryRepository gmhRepository;

    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

    @AfterEach
    void cleanUp() {
        gmhRepository.deleteAll();
        cartRepository.deleteAll();
        categoryRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @Transactional
    void findCartsByGroupIdAndIsDeletedFalseOrderByDatePurchasedDesc() throws ParseException {
        var user = createDataService.createUser("Test-User");
        var groupTest = createDataService.createGroup("Test-Group", user);
        var categoryTest = createDataService.createCategory("Test-Category", groupTest);

        createDataService.createCart("Test-Cart", 50.0, groupTest, user, categoryTest, false, "2023-11-15", 1);
        createDataService.createListOfCartsForGroupAndUser(7, 80.0, groupTest, user, categoryTest, false, "2023-11-16", 1);
        createDataService.createCart("Test-Cart", 100.0, groupTest, user, categoryTest, false, "2023-11-17", 1);
        createDataService.createCart("Test-Cart", 150.0, groupTest, user, categoryTest, false, "2023-11-19", 1);
        createDataService.createCart("Test-Cart", 200.0, groupTest, user, categoryTest, true, "2023-11-15", 1);
        createDataService.createCart("Test-Cart", 250.0, groupTest, user, categoryTest, true, "2023-11-16", 1);

        var carts = cartRepository.findCartsByGroupIdAndIsDeletedFalseOrderByDatePurchasedDesc(groupTest.getId());

        // should be 10 carts in group 'groupTest'
        assertEquals(10, carts.size());
        // should be first element
        assertEquals(150.0, carts.get(0).getAmount());
        // should be last element
        assertEquals(50.0, carts.get(carts.size() - 1).getAmount());
    }

    @Test
    @Transactional
    void getGroupMemberCountForCartDatePurchased() throws ParseException {
        var owner = createDataService.createUser("Test-User");
        var member1 = createDataService.createUser("Test-Member-1");
        var member2 = createDataService.createUser("Test-Member-2");
        var groupTest = createDataService.createGroup("Test-Group", owner);
        var categoryTest = createDataService.createCategory("Test-Category", groupTest);

        createDataService.createCart("Test-Cart", 100.0, groupTest, owner, categoryTest, false, "2023-11-15", 1);

        // member count should be 3
        createDataService.createGmh(groupTest.getId(), owner.getId(), TypeEnum.OWNER, "2023-11-15", "null");
        createDataService.createGmh(groupTest.getId(), member1.getId(), TypeEnum.MEMBER, "2023-11-15", "null");
        createDataService.createGmh(groupTest.getId(), member2.getId(), TypeEnum.MEMBER, "2023-11-15", "null");
        assertEquals(3, cartRepository.getGroupMemberCountForCartDatePurchased(formatter.parse("2023-11-15"), groupTest.getId()));

        gmhRepository.deleteAll();

        // member count should be 2
        createDataService.createGmh(groupTest.getId(), owner.getId(), TypeEnum.OWNER, "2023-11-15", "null");
        createDataService.createGmh(groupTest.getId(), member1.getId(), TypeEnum.MEMBER, "2023-11-15", "null");
        createDataService.createGmh(groupTest.getId(), member2.getId(), TypeEnum.MEMBER, "2023-11-16", "null");
        assertEquals(2, cartRepository.getGroupMemberCountForCartDatePurchased(formatter.parse("2023-11-15"), groupTest.getId()));

        gmhRepository.deleteAll();

        // member count should be 2
        createDataService.createGmh(groupTest.getId(), owner.getId(), TypeEnum.OWNER, "2023-11-15", "null");
        createDataService.createGmh(groupTest.getId(), member1.getId(), TypeEnum.MEMBER, "2023-11-15", "null");
        createDataService.createGmh(groupTest.getId(), member2.getId(), TypeEnum.MEMBER, "2023-11-15", "2023-11-15");
        assertEquals(2, cartRepository.getGroupMemberCountForCartDatePurchased(formatter.parse("2023-11-15"), groupTest.getId()));

        gmhRepository.deleteAll();

        // member count should be 1
        createDataService.createGmh(groupTest.getId(), owner.getId(), TypeEnum.OWNER, "2023-11-15", "null");
        createDataService.createGmh(groupTest.getId(), member1.getId(), TypeEnum.MEMBER, "2023-11-10", "2023-11-14");
        createDataService.createGmh(groupTest.getId(), member2.getId(), TypeEnum.MEMBER, "2023-11-15", "2023-11-15");
        assertEquals(1, cartRepository.getGroupMemberCountForCartDatePurchased(formatter.parse("2023-11-15"), groupTest.getId()));

        gmhRepository.deleteAll();

    }

    @Test
    void getSpendingsMonthlyTotalSumAmount() throws ParseException {
        var owner = createDataService.createUser("Test-User");
        var groupTest = createDataService.createGroup("Test-Group", owner);
        var categoryTest = createDataService.createCategory("Test-Category", groupTest);

        createDataService.createListOfCartsForGroupAndUser(5, 10.0, groupTest, owner, categoryTest, false, "2023-01-31", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 20.0, groupTest, owner, categoryTest, false, "2023-02-01", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 30.0, groupTest, owner, categoryTest, false, "2023-03-31", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 30.0, groupTest, owner, categoryTest, true, "2023-03-31", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 40.0, groupTest, owner, categoryTest, false, "2023-04-01", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 50.0, groupTest, owner, categoryTest, false, "2023-05-31", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 60.0, groupTest, owner, categoryTest, false, "2023-06-01", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 70.0, groupTest, owner, categoryTest, false, "2023-07-31", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 70.0, groupTest, owner, categoryTest, true, "2023-07-31", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 80.0, groupTest, owner, categoryTest, false, "2023-08-01", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 90.0, groupTest, owner, categoryTest, false, "2023-09-30", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 100.0, groupTest, owner, categoryTest, false, "2023-10-01", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 110.0, groupTest, owner, categoryTest, false, "2023-11-30", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 110.0, groupTest, owner, categoryTest, true, "2023-11-30", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 120.0, groupTest, owner, categoryTest, false, "2023-12-01", 1);

        var spendingsOverviewMonthlyTotalSumAmountDto = cartRepository.getSpendingsMonthlyTotalSumAmount(2023, groupTest.getId());

        assertEquals(12, spendingsOverviewMonthlyTotalSumAmountDto.size());

        assertEquals(600.0, spendingsOverviewMonthlyTotalSumAmountDto.get(0).getSumAmountTotalPerMonth());
        assertEquals(550.0, spendingsOverviewMonthlyTotalSumAmountDto.get(1).getSumAmountTotalPerMonth());
        assertEquals(500.0, spendingsOverviewMonthlyTotalSumAmountDto.get(2).getSumAmountTotalPerMonth());
        assertEquals(450.0, spendingsOverviewMonthlyTotalSumAmountDto.get(3).getSumAmountTotalPerMonth());
        assertEquals(400.0, spendingsOverviewMonthlyTotalSumAmountDto.get(4).getSumAmountTotalPerMonth());
        assertEquals(350.0, spendingsOverviewMonthlyTotalSumAmountDto.get(5).getSumAmountTotalPerMonth());
        assertEquals(300.0, spendingsOverviewMonthlyTotalSumAmountDto.get(6).getSumAmountTotalPerMonth());
        assertEquals(250.0, spendingsOverviewMonthlyTotalSumAmountDto.get(7).getSumAmountTotalPerMonth());
        assertEquals(200.0, spendingsOverviewMonthlyTotalSumAmountDto.get(8).getSumAmountTotalPerMonth());
        assertEquals(150.0, spendingsOverviewMonthlyTotalSumAmountDto.get(9).getSumAmountTotalPerMonth());
        assertEquals(100.0, spendingsOverviewMonthlyTotalSumAmountDto.get(10).getSumAmountTotalPerMonth());
        assertEquals(50.0, spendingsOverviewMonthlyTotalSumAmountDto.get(11).getSumAmountTotalPerMonth());

        for (int i = 0; i < spendingsOverviewMonthlyTotalSumAmountDto.size(); i++) {
            assertEquals((12 - i), spendingsOverviewMonthlyTotalSumAmountDto.get(i).getMonth());
            assertEquals(2023, spendingsOverviewMonthlyTotalSumAmountDto.get(i).getYear());
        }
    }

    @Test
    void getSpendingsYearlyTotalSumAmount() throws ParseException {
        var owner = createDataService.createUser("Test-User");
        var groupTest = createDataService.createGroup("Test-Group", owner);
        var categoryTest = createDataService.createCategory("Test-Category", groupTest);

        createDataService.createListOfCartsForGroupAndUser(5, 50.0, groupTest, owner, categoryTest, false, "2024-05-31", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 40.0, groupTest, owner, categoryTest, false, "2023-04-01", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 40.0, groupTest, owner, categoryTest, true, "2023-04-01", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 30.0, groupTest, owner, categoryTest, false, "2022-03-31", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 20.0, groupTest, owner, categoryTest, false, "2021-02-01", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 10.0, groupTest, owner, categoryTest, false, "2020-01-31", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 30.0, groupTest, owner, categoryTest, true, "2025-03-31", 1);

        var spendingsOverviewYearlyTotalSumAmountDto = cartRepository.getSpendingsYearlyTotalSumAmount(groupTest.getId());

        assertEquals(5, spendingsOverviewYearlyTotalSumAmountDto.size());

        assertEquals(250.0, spendingsOverviewYearlyTotalSumAmountDto.get(0).getSumAmountTotalPerYear());
        assertEquals(2024, spendingsOverviewYearlyTotalSumAmountDto.get(0).getYear());

        assertEquals(200.0, spendingsOverviewYearlyTotalSumAmountDto.get(1).getSumAmountTotalPerYear());
        assertEquals(2023, spendingsOverviewYearlyTotalSumAmountDto.get(1).getYear());

        assertEquals(150.0, spendingsOverviewYearlyTotalSumAmountDto.get(2).getSumAmountTotalPerYear());
        assertEquals(2022, spendingsOverviewYearlyTotalSumAmountDto.get(2).getYear());

        assertEquals(100.0, spendingsOverviewYearlyTotalSumAmountDto.get(3).getSumAmountTotalPerYear());
        assertEquals(2021, spendingsOverviewYearlyTotalSumAmountDto.get(3).getYear());

        assertEquals(50.0, spendingsOverviewYearlyTotalSumAmountDto.get(4).getSumAmountTotalPerYear());
        assertEquals(2020, spendingsOverviewYearlyTotalSumAmountDto.get(4).getYear());
    }

    @Test
    void getTotalAmountAllYears() throws ParseException {
        var owner = createDataService.createUser("Test-User");
        var groupTest = createDataService.createGroup("Test-Group", owner);
        var categoryTest = createDataService.createCategory("Test-Category", groupTest);

        createDataService.createListOfCartsForGroupAndUser(5, 50.0, groupTest, owner, categoryTest, false, "2024-05-31", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 40.0, groupTest, owner, categoryTest, false, "2023-04-01", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 40.0, groupTest, owner, categoryTest, true, "2023-04-01", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 30.0, groupTest, owner, categoryTest, false, "2022-03-31", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 20.0, groupTest, owner, categoryTest, false, "2021-02-01", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 10.0, groupTest, owner, categoryTest, false, "2020-01-31", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 30.0, groupTest, owner, categoryTest, true, "2025-03-31", 1);

        var totalAmountAllYears = cartRepository.getTotalAmountAllYears(groupTest.getId());

        assertEquals(750.0, totalAmountAllYears);
    }

    @Test
    void getAvailableYearsForGroup() throws ParseException {
        var owner = createDataService.createUser("Test-User");
        var groupTest = createDataService.createGroup("Test-Group", owner);
        var categoryTest = createDataService.createCategory("Test-Category", groupTest);

        createDataService.createListOfCartsForGroupAndUser(5, 50.0, groupTest, owner, categoryTest, false, "2024-05-31", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 40.0, groupTest, owner, categoryTest, false, "2023-04-01", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 40.0, groupTest, owner, categoryTest, true, "2023-04-01", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 30.0, groupTest, owner, categoryTest, false, "2022-03-31", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 20.0, groupTest, owner, categoryTest, false, "2021-02-01", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 10.0, groupTest, owner, categoryTest, false, "2020-01-31", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 30.0, groupTest, owner, categoryTest, true, "2025-03-31", 1);

        var availableYearsForGroup = cartRepository.getAvailableYearsForGroup(groupTest.getId());

        assertEquals(5, availableYearsForGroup.size());

        assertEquals(2024, availableYearsForGroup.get(0));
        assertEquals(2023, availableYearsForGroup.get(1));
        assertEquals(2022, availableYearsForGroup.get(2));
        assertEquals(2021, availableYearsForGroup.get(3));
        assertEquals(2020, availableYearsForGroup.get(4));
    }

    @Test
    void getAveragePerUserAndYear() throws ParseException {
        // given: Nutzer, Kategorie und Gruppe werden angelegt
        var owner = createDataService.createUser("Test-User");
        var member1 = createDataService.createUser("Test-Member-1");
        var member2 = createDataService.createUser("Test-Member-2");
        var groupTest = createDataService.createGroup("Test-Group", owner);
        var categoryTest = createDataService.createCategory("Test-Category", groupTest);

        // when: unterschiedliche Anzahl an carts werden je Nutzer erzeugt, alle mit dem gleichen Datum für datePurchased
        createDataService.createListOfCartsForGroupAndUser(3, 90.0, groupTest, owner, categoryTest, false, "2023-11-15", 3);
        createDataService.createListOfCartsForGroupAndUser(5, 60.0, groupTest, member1, categoryTest, false, "2023-11-15", 3);
        createDataService.createListOfCartsForGroupAndUser(1, 30.0, groupTest, member2, categoryTest, false, "2023-11-15", 3);
        // die beiden carts sollten nicht in die Berechnung einfließen
        createDataService.createListOfCartsForGroupAndUser(2, 120.0, groupTest, member1, categoryTest, true, "2023-11-15", 3);

        // and: GroupMembershipHistory je Nutzer mit Start der Mitgliedschaft zum Zeitpunkt datePurchased der carts
        createDataService.createGmh(groupTest.getId(), owner.getId(), TypeEnum.OWNER, "2023-11-15", "null");
        createDataService.createGmh(groupTest.getId(), member1.getId(), TypeEnum.MEMBER, "2023-11-15", "null");
        createDataService.createGmh(groupTest.getId(), member2.getId(), TypeEnum.MEMBER, "2023-11-15", "null");

        // then: soll die Summe für averagePerMember je Nutzer, Monat und Jahr korrekt berechnet werden
        var spendingsOverviewAverageDto = cartRepository.getAveragePerUserAndYear(groupTest.getId(), 2023);

        assertEquals(9, spendingsOverviewAverageDto.size());

        assertAll( "Assertions for Test-User",
            () -> assertEquals(10.0, spendingsOverviewAverageDto.get(0).getAveragePerMember()),
            () -> assertEquals(1, spendingsOverviewAverageDto.get(0).getUserId()),
            () -> assertEquals(2023, spendingsOverviewAverageDto.get(0).getYear()),
            () -> assertEquals(11, spendingsOverviewAverageDto.get(0).getMonth()),

            () -> assertEquals(100.0, spendingsOverviewAverageDto.get(1).getAveragePerMember()),
            () -> assertEquals(1, spendingsOverviewAverageDto.get(1).getUserId()),
            () -> assertEquals(2023, spendingsOverviewAverageDto.get(1).getYear()),
            () -> assertEquals(11, spendingsOverviewAverageDto.get(1).getMonth()),

            () -> assertEquals(90.0, spendingsOverviewAverageDto.get(2).getAveragePerMember()),
            () -> assertEquals(1, spendingsOverviewAverageDto.get(2).getUserId()),
            () -> assertEquals(2023, spendingsOverviewAverageDto.get(2).getYear()),
            () -> assertEquals(11, spendingsOverviewAverageDto.get(2).getMonth())
        );
        assertAll( "Assertions for Test-Member-1",
            () -> assertEquals(10.0, spendingsOverviewAverageDto.get(3).getAveragePerMember()),
            () -> assertEquals(2, spendingsOverviewAverageDto.get(3).getUserId()),
            () -> assertEquals(2023, spendingsOverviewAverageDto.get(3).getYear()),
            () -> assertEquals(11, spendingsOverviewAverageDto.get(3).getMonth()),

            () -> assertEquals(100.0, spendingsOverviewAverageDto.get(4).getAveragePerMember()),
            () -> assertEquals(2, spendingsOverviewAverageDto.get(4).getUserId()),
            () -> assertEquals(2023, spendingsOverviewAverageDto.get(4).getYear()),
            () -> assertEquals(11, spendingsOverviewAverageDto.get(4).getMonth()),

            () -> assertEquals(90.0, spendingsOverviewAverageDto.get(5).getAveragePerMember()),
            () -> assertEquals(2, spendingsOverviewAverageDto.get(5).getUserId()),
            () -> assertEquals(2023, spendingsOverviewAverageDto.get(5).getYear()),
            () -> assertEquals(11, spendingsOverviewAverageDto.get(5).getMonth())
        );
        assertAll( "Assertions for Test-Member-2",
            () -> assertEquals(10.0, spendingsOverviewAverageDto.get(6).getAveragePerMember()),
            () -> assertEquals(3, spendingsOverviewAverageDto.get(6).getUserId()),
            () -> assertEquals(2023, spendingsOverviewAverageDto.get(6).getYear()),
            () -> assertEquals(11, spendingsOverviewAverageDto.get(6).getMonth()),

            () -> assertEquals(100.0, spendingsOverviewAverageDto.get(7).getAveragePerMember()),
            () -> assertEquals(3, spendingsOverviewAverageDto.get(7).getUserId()),
            () -> assertEquals(2023, spendingsOverviewAverageDto.get(7).getYear()),
            () -> assertEquals(11, spendingsOverviewAverageDto.get(7).getMonth()),

            () -> assertEquals(90.0, spendingsOverviewAverageDto.get(8).getAveragePerMember()),
            () -> assertEquals(3, spendingsOverviewAverageDto.get(8).getUserId()),
            () -> assertEquals(2023, spendingsOverviewAverageDto.get(8).getYear()),
            () -> assertEquals(11, spendingsOverviewAverageDto.get(8).getMonth())
        );

        // cleanUp
        gmhRepository.deleteAll();
        cartRepository.deleteAll();


        // when: unterschiedliche Anzahl an carts werden je Nutzer erzeugt, mit unterschiedlichem Datum für datePurchased
        createDataService.createListOfCartsForGroupAndUser(3, 90.0, groupTest, owner, categoryTest, false, "2023-11-15", 2);
        createDataService.createListOfCartsForGroupAndUser(5, 60.0, groupTest, member1, categoryTest, false, "2023-11-15", 2);
        createDataService.createListOfCartsForGroupAndUser(1, 30.0, groupTest, member2, categoryTest, false, "2023-11-30", 3);


        // and: GroupMembershipHistory je Nutzer mit Start der Mitgliedschaft zum Zeitpunkt datePurchased der carts
        createDataService.createGmh(groupTest.getId(), owner.getId(), TypeEnum.OWNER, "2023-11-15", "null");
        createDataService.createGmh(groupTest.getId(), member1.getId(), TypeEnum.MEMBER, "2023-11-15", "null");
        createDataService.createGmh(groupTest.getId(), member2.getId(), TypeEnum.MEMBER, "2023-11-16", "null");

        var spendingsOverviewAverageDto2 = cartRepository.getAveragePerUserAndYear(groupTest.getId(), 2023);

        assertEquals(7, spendingsOverviewAverageDto2.size());

        assertAll( "Assertions for Test-User",
                () -> assertEquals(10.0, spendingsOverviewAverageDto2.get(0).getAveragePerMember()),
                () -> assertEquals(1, spendingsOverviewAverageDto2.get(0).getUserId()),
                () -> assertEquals(2023, spendingsOverviewAverageDto2.get(0).getYear()),
                () -> assertEquals(11, spendingsOverviewAverageDto2.get(0).getMonth()),

                () -> assertEquals(150.0, spendingsOverviewAverageDto2.get(1).getAveragePerMember()),
                () -> assertEquals(1, spendingsOverviewAverageDto2.get(1).getUserId()),
                () -> assertEquals(2023, spendingsOverviewAverageDto2.get(1).getYear()),
                () -> assertEquals(11, spendingsOverviewAverageDto2.get(1).getMonth()),

                () -> assertEquals(135.0, spendingsOverviewAverageDto2.get(2).getAveragePerMember()),
                () -> assertEquals(1, spendingsOverviewAverageDto2.get(2).getUserId()),
                () -> assertEquals(2023, spendingsOverviewAverageDto2.get(2).getYear()),
                () -> assertEquals(11, spendingsOverviewAverageDto2.get(2).getMonth())
        );
        assertAll( "Assertions for Test-Member-1",
                () -> assertEquals(10.0, spendingsOverviewAverageDto2.get(3).getAveragePerMember()),
                () -> assertEquals(2, spendingsOverviewAverageDto2.get(3).getUserId()),
                () -> assertEquals(2023, spendingsOverviewAverageDto2.get(3).getYear()),
                () -> assertEquals(11, spendingsOverviewAverageDto2.get(3).getMonth()),

                () -> assertEquals(150.0, spendingsOverviewAverageDto2.get(4).getAveragePerMember()),
                () -> assertEquals(2, spendingsOverviewAverageDto2.get(4).getUserId()),
                () -> assertEquals(2023, spendingsOverviewAverageDto2.get(4).getYear()),
                () -> assertEquals(11, spendingsOverviewAverageDto2.get(4).getMonth()),

                () -> assertEquals(135.0, spendingsOverviewAverageDto2.get(5).getAveragePerMember()),
                () -> assertEquals(2, spendingsOverviewAverageDto2.get(5).getUserId()),
                () -> assertEquals(2023, spendingsOverviewAverageDto2.get(5).getYear()),
                () -> assertEquals(11, spendingsOverviewAverageDto2.get(5).getMonth())
        );
        assertAll( "Assertions for Test-Member-2",
                () -> assertEquals(10.0, spendingsOverviewAverageDto2.get(6).getAveragePerMember()),
                () -> assertEquals(3, spendingsOverviewAverageDto2.get(6).getUserId()),
                () -> assertEquals(2023, spendingsOverviewAverageDto2.get(6).getYear()),
                () -> assertEquals(11, spendingsOverviewAverageDto2.get(6).getMonth())
        );

        // cleanUp
        gmhRepository.deleteAll();
        cartRepository.deleteAll();

        // when: unterschiedliche Anzahl an carts werden je Nutzer erzeugt, mit unterschiedlichem Datum für datePurchased
        createDataService.createListOfCartsForGroupAndUser(3, 90.0, groupTest, owner, categoryTest, false, "2023-11-15", 1);
        createDataService.createListOfCartsForGroupAndUser(5, 60.0, groupTest, member1, categoryTest, true, "2023-11-15", 1);
        createDataService.createListOfCartsForGroupAndUser(1, 30.0, groupTest, member2, categoryTest, false, "2023-11-30", 2);


        // and: GroupMembershipHistory je Nutzer
        createDataService.createGmh(groupTest.getId(), owner.getId(), TypeEnum.OWNER, "2023-11-15", "null");
        createDataService.createGmh(groupTest.getId(), member1.getId(), TypeEnum.MEMBER, "2023-11-15", "2023-11-15");
        createDataService.createGmh(groupTest.getId(), member2.getId(), TypeEnum.MEMBER, "2023-11-16", "null");

        var spendingsOverviewAverageDto3 = cartRepository.getAveragePerUserAndYear(groupTest.getId(), 2023);

        assertEquals(3, spendingsOverviewAverageDto3.size());

        assertAll( "Assertions for Test-User",
                () -> assertEquals(15.0, spendingsOverviewAverageDto3.get(0).getAveragePerMember()),
                () -> assertEquals(1, spendingsOverviewAverageDto3.get(0).getUserId()),
                () -> assertEquals(2023, spendingsOverviewAverageDto3.get(0).getYear()),
                () -> assertEquals(11, spendingsOverviewAverageDto3.get(0).getMonth()),

                () -> assertEquals(270.0, spendingsOverviewAverageDto3.get(1).getAveragePerMember()),
                () -> assertEquals(1, spendingsOverviewAverageDto3.get(1).getUserId()),
                () -> assertEquals(2023, spendingsOverviewAverageDto3.get(1).getYear()),
                () -> assertEquals(11, spendingsOverviewAverageDto3.get(1).getMonth())
        );
        assertAll( "Assertions for Test-Member-2",
                () -> assertEquals(15.0, spendingsOverviewAverageDto3.get(2).getAveragePerMember()),
                () -> assertEquals(3, spendingsOverviewAverageDto3.get(2).getUserId()),
                () -> assertEquals(2023, spendingsOverviewAverageDto3.get(2).getYear()),
                () -> assertEquals(11, spendingsOverviewAverageDto3.get(2).getMonth())
        );
    }

    @Test
    void getAveragePerUserAndTotalYears() {
    }

    @Test
    void getSpendingsAmountPerMonthAndUser() {
    }

    @Test
    void getSpendingsAmountPerYearAndUser() {
    }

    @Test
    void getSpendingsAmountPerUserYearly() {
    }

    @Test
    void getSpendingsAmountPerUserAndTotalYears() {
    }
}
