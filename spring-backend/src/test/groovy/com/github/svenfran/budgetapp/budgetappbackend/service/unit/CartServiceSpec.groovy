package com.github.svenfran.budgetapp.budgetappbackend.service.unit

import com.github.svenfran.budgetapp.budgetappbackend.constants.RecurrenceType
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto
import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart
import com.github.svenfran.budgetapp.budgetappbackend.entity.CartTemplate
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category
import com.github.svenfran.budgetapp.budgetappbackend.service.CartService
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate
import java.time.ZoneId

class CartServiceSpec extends Specification {

    @Shared
    def cartService = new CartService()

    static Date toDate(LocalDate localDate) {
        Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
    }

    def category = new Category(id: 100L)
    def datePurchasedEntity = toDate(LocalDate.of(2025, 9, 1))
    def datePurchasedDto = toDate(LocalDate.of(2025, 9, 1))
    @Shared
    def datePurchasedTest = Date.from(LocalDate.of(2025, 10, 1).atStartOfDay(ZoneId.systemDefault()).toInstant())

    Cart baseEntityWithTemplate(RecurrenceType type = RecurrenceType.MONTHLY) {
        def cart = baseEntity()
        cart.template = new CartTemplate(id: 991L, recurrenceType: type, active: true)
        return cart
    }

    Cart baseEntity() {
        def cart = new Cart()
        cart.id = 42L
        cart.title = "Testcart"
        cart.description = "Beschreibung"
        cart.amount = 99.99
        cart.datePurchased = datePurchasedEntity
        cart.category = category
        return cart
    }

    CartDto baseDto() {
        def dto = new CartDto()
        dto.id = 42L
        dto.title = "Testcart"
        dto.description = "Beschreibung"
        dto.amount = 99.99
        dto.datePurchased = datePurchasedDto
        dto.categoryDto = new CategoryDto(id: 100L)
        return dto
    }

    @Unroll
    def "hasCartChanged erkennt Änderungen bei Feld #feld"() {
        given:
        def entity = baseEntity()
        def dto = baseDto()

        when: "wir das Feld im DTO ändern"
        change(dto)

        then:
        cartService.hasCartChanged(dto, entity) == erwartet

        where:
        feld           | change                                         || erwartet
        "title"        | { it.title = "Neu" }                           || true
        "description"  | { it.description = "Andere" }                  || true
        "amount"       | { it.amount = 111.11 }                         || true
        "datePurchased"| { it.datePurchased = datePurchasedTest}        || true
        "category"     | { it.categoryDto = new CategoryDto(id: 200L) } || true
        "templateUpd"  | { it.templateUpdateSelected = true }           || true
        "noChange"     | { /* nichts ändern */ }                        || false
    }

    @Unroll
    def "hasRecurrenceChanged erkennt Änderung korrekt (old=#oldType, new=#newType)"() {
        given:
        def entity = baseEntityWithTemplate(oldType)
        def dto = baseDto()
        dto.recurrenceType = newType

        expect:
        cartService.hasCartChanged(dto, entity) == erwartet

        where:
        oldType                | newType                || erwartet
        RecurrenceType.MONTHLY | RecurrenceType.MONTHLY || false   // keine Änderung
        RecurrenceType.MONTHLY | RecurrenceType.NONE    || true    // deaktiviert
        RecurrenceType.MONTHLY | RecurrenceType.DAILY   || true    // geändert
        RecurrenceType.DAILY   | RecurrenceType.WEEKLY  || true
        RecurrenceType.WEEKLY  | RecurrenceType.WEEKLY  || false
    }

    @Unroll
    def "hasRecurrenceChanged erkennt Änderung korrekt wenn bisher KEIN Template existierte (newType=#newType)"() {
        given:
        def entity = baseEntity() // kein Template
        def dto = baseDto()
        dto.recurrenceType = newType

        expect:
        cartService.hasCartChanged(dto, entity) == erwartet

        where:
        newType                  || erwartet
        RecurrenceType.NONE      || false   // bleibt NONE → keine Änderung
        RecurrenceType.MONTHLY   || true    // neues Template notwendig
        RecurrenceType.DAILY     || true
    }
}
