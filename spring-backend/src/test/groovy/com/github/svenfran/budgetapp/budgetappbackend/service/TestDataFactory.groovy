package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.constants.RecurrenceType
import com.github.svenfran.budgetapp.budgetappbackend.constants.TypeEnum
import com.github.svenfran.budgetapp.budgetappbackend.entity.*

import java.time.LocalDate
import java.time.ZoneId

class TestDataFactory {

    static User buildUser(String name = "testuser", String email = "test@example.com") {
        new User(
                name: name,
                email: email,
                password: "pw"
        )
    }

    static GroupMembershipHistory buildGmh(user, group) {
        new GroupMembershipHistory(
                membershipStart: asDate(LocalDate.now().plusYears(-1)),
                membershipEnd: null,
                type: TypeEnum.OWNER,
                userId: user.id,
                groupId: group.id
        )
    }

    static Group buildGroup(String name = "Test Group") {
        new Group(name: name)
    }

    static Category buildCategory(Group group, String name = "Food") {
        new Category(
                name: name,
                group: group
        )
    }

    static Cart buildCart(User user, Group group, Category category,
                          String title = "Default Cart",
                          Double amount = 100,
                          Date date = asDate(LocalDate.now().plusDays(-3))) {
        new Cart(
                title: title,
                description: "desc of $title",
                amount: amount,
                averagePerMember: amount,
                datePurchased: date,
                category: category,
                user: user,
                group: group
        )
    }

    static CartTemplate buildTemplate(Cart cart, RecurrenceType recurrenceType = RecurrenceType.MONTHLY, LocalDate nextDate) {
        new CartTemplate(
                title: cart.title,
                amount: cart.amount,
                description: cart.description,
                recurrenceType: recurrenceType,
                startDate: asLocalDate(cart.datePurchased),
                nextExecutionDate: nextDate,
                active: true,
                userId: cart.user.id,
                groupId: cart.group.id,
                categoryId: cart.category.id
        )
    }

    static Date asDate(LocalDate date) {
        Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())
    }

    static LocalDate asLocalDate(Date localDate) {
        localDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    }
}

