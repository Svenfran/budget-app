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

    @Shared
    def datePurchasedTest = toDate(LocalDate.of(2025, 10, 1))

    def category = new Category(id: 100L)

    def datePurchased = toDate(LocalDate.of(2025, 9, 1))

    static Date toDate(LocalDate localDate) {
        Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
    }

    static LocalDate toLocalDate(Date date) {
        date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    }

    Cart baseEntityWithTemplate(RecurrenceType type = RecurrenceType.MONTHLY) {
        def cart = baseEntity()
        cart.template = baseTemplate(type)
        return cart
    }

    CartTemplate baseTemplate(RecurrenceType type) {
        def template = new CartTemplate()
        template.id = 991L
        template.title = "Testcart"
        template.description = "Beschreibung"
        template.amount = 99.99
        template.active = true
        template.recurrenceType = type
        template.startDate = LocalDate.now()
        template.nextExecutionDate = toLocalDate(datePurchased)
        template.endDate = null
        return template
    }

    Cart baseEntity() {
        def cart = new Cart()
        cart.id = 42L
        cart.title = "Testcart"
        cart.description = "Beschreibung"
        cart.amount = 99.99
        cart.datePurchased = datePurchased
        cart.category = category
        return cart
    }

    CartDto baseDto() {
        def dto = new CartDto()
        dto.id = 42L
        dto.title = "Testcart"
        dto.description = "Beschreibung"
        dto.amount = 99.99
        dto.datePurchased = datePurchased
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
        def hasCartChanged = CartService.getDeclaredMethod("hasCartChanged", CartDto, Cart)
        hasCartChanged.setAccessible(true)
        hasCartChanged.invoke(cartService, dto, entity) == erwartet
//        cartService.hasCartChanged(dto, entity) == erwartet

        where:
        feld           | change                                         || erwartet
        "title"        | { it.title = "Neu" }                           || true
        "description"  | { it.description = "Andere" }                  || true
        "amount"       | { it.amount = 111.11 }                         || true
        "datePurchased"| { it.datePurchased = datePurchasedTest}        || true
        "category"     | { it.categoryDto = new CategoryDto(id: 200L) } || true
        "noChange"     | { /* nichts ändern */ }                        || false
    }

    @Unroll
    def "hasRecurrenceChanged erkennt Änderung korrekt (old=#oldType, new=#newType)"() {
        given:
        def entity = baseEntityWithTemplate(oldType)
        def dto = baseDto()
        dto.recurrenceType = newType

        expect:
        def hasRecurrenceTypeChanged = CartService.getDeclaredMethod("hasRecurrenceTypeChanged", CartDto, Cart)
        hasRecurrenceTypeChanged.setAccessible(true)
        hasRecurrenceTypeChanged.invoke(cartService, dto, entity) == erwartet
//        cartService.hasRecurrenceTypeChanged(dto, entity) == erwartet

        where:
        oldType                | newType                || erwartet
        RecurrenceType.MONTHLY | RecurrenceType.MONTHLY || false   // keine Änderung
        RecurrenceType.MONTHLY | RecurrenceType.NONE    || true    // deaktiviert
        RecurrenceType.MONTHLY | RecurrenceType.DAILY   || true    // geändert
        RecurrenceType.DAILY   | RecurrenceType.WEEKLY  || true
        RecurrenceType.WEEKLY  | RecurrenceType.WEEKLY  || false
    }

}
