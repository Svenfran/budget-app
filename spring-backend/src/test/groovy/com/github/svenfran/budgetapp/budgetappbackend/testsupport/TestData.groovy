package com.github.svenfran.budgetapp.budgetappbackend.testsupport

import com.github.svenfran.budgetapp.budgetappbackend.constants.RecurrenceType
import com.github.svenfran.budgetapp.budgetappbackend.constants.TokenType
import com.github.svenfran.budgetapp.budgetappbackend.constants.TypeEnum
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto
import com.github.svenfran.budgetapp.budgetappbackend.entity.*
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils

import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/**
 * Nicht persistierte, fachlich gültige Testobjekte (Unit-Specs und Bausteine für {@link Fixtures}).
 *
 * - Jedes Feld per Named Argument überschreibbar: TestData.user(name: 'ben').
 * - Namen und E-Mails sind eindeutig (laufende Nummer), Collections initialisiert.
 * - Feste Daten über REFERENCE_DATE statt LocalDate.now(); Umrechnung nur über DateUtils.
 * - Neue Methoden erst mit dem ersten Test, der sie braucht.
 */
class TestData {

    static final LocalDate REFERENCE_DATE = LocalDate.of(2025, 6, 15)
    private static final AtomicLong SEQUENCE = new AtomicLong()

    static long nextId() { SEQUENCE.incrementAndGet() }

    static User user(Map overrides = [:]) {
        long n = nextId()
        new User([name: "user$n", email: "user$n@divvy.test", password: 'secret',
                  groups: [] as Set, groupList: [] as Set, carts: [] as Set] + overrides)
    }

    static Group group(Map overrides = [:]) {
        new Group([name: "Gruppe ${nextId()}", members: [] as Set, carts: [] as Set,
                   categories: [] as Set, shoppingLists: [] as Set] + overrides)
    }

    static Category category(Map overrides = [:]) {
        new Category([name: "Kategorie ${nextId()}"] + overrides)
    }

    static GroupMembershipHistory membership(Map overrides = [:]) {
        new GroupMembershipHistory([type: TypeEnum.MEMBER,
                                    membershipStart: DateUtils.toDate(REFERENCE_DATE.minusYears(1)),
                                    membershipEnd: null] + overrides)
    }

    static Cart cart(Map overrides = [:]) {
        new Cart([title: 'Einkauf', description: 'Wocheneinkauf', amount: 40.0d, averagePerMember: 40.0d,
                  datePurchased: DateUtils.toDate(REFERENCE_DATE)] + overrides)
    }

    static CartTemplate cartTemplate(Map overrides = [:]) {
        new CartTemplate([title: 'Einkauf', description: 'Wocheneinkauf', amount: 40.0d,
                          recurrenceType: RecurrenceType.MONTHLY, active: true, startDate: REFERENCE_DATE,
                          nextExecutionDate: REFERENCE_DATE, endDate: null] + overrides)
    }

    static ShoppingList shoppingList(Map overrides = [:]) {
        new ShoppingList([name: "Liste ${nextId()}", shoppingItems: [] as Set] + overrides)
    }

    /** Gültiges Token (weder abgelaufen noch widerrufen); Platzhalter statt echtem JWT. Nutzer setzt der Aufrufer. */
    static Token token(Map overrides = [:]) {
        new Token([token: "token-${nextId()}", tokenType: TokenType.BEARER, expired: false, revoked: false,
                   deviceId: 'device-1'] + overrides)
    }

    static CartDto cartDto(Map overrides = [:]) {
        new CartDto([title: 'Einkauf', description: 'Wocheneinkauf', amount: 40.0d,
                     datePurchased: DateUtils.toDate(REFERENCE_DATE), recurrenceType: RecurrenceType.NONE] + overrides)
    }

    static CategoryDto categoryDto(Category category) {
        new CategoryDto(id: category.id, name: category.name)
    }
}
