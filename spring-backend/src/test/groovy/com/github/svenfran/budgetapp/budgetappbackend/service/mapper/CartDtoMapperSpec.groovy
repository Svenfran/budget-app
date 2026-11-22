package com.github.svenfran.budgetapp.budgetappbackend.service.mapper

import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import spock.lang.Specification

import java.time.LocalDate

class CartDtoMapperSpec extends Specification {

    def mapper = new CartDtoMapper()

    def "Cart-DTO wird mit Gruppe, Kategorie, Nutzer und Durchschnitt pro Mitglied übernommen"() {
        given:
        def dto = TestData.cartDto(id: 1L, title: 'Pizza', description: 'Freitag', amount: 55d,
                datePurchased: TestData.REFERENCE_DATE)
        def group = TestData.group(id: 10L)
        def user = TestData.user(id: 5L)
        def category = TestData.category(id: 20L)

        when:
        def cart = mapper.cartDtoToEntity(dto, category, user, group, 5)

        then:
        cart.id == 1L
        cart.title == 'Pizza'
        cart.description == 'Freitag'
        cart.amount == 55d
        cart.averagePerMember == 11d
        !cart.deleted
        DateUtils.toLocalDate(cart.datePurchased) == TestData.REFERENCE_DATE
        cart.group.id == 10L
        cart.user.id == 5L
        cart.category.id == 20L
    }

    def "Durchschnitt pro Mitglied ist 0 bei 0 Mitgliedern"() {
        given:
        def dto = TestData.cartDto(amount: 55d, datePurchased: TestData.REFERENCE_DATE)

        when:
        def cart = mapper.cartDtoToEntity(dto, TestData.category(), TestData.user(), TestData.group(), 0)

        then:
        cart.averagePerMember == 0d
    }

    def "Kaufdatum #tag bleibt beim Weg Entity → DTO → Entity derselbe Kalendertag"() {
        given: 'ein Cart, wie Hibernate ihn aus der DATE-Spalte liest (Mitternacht in der JVM-Zone)'
        def group = TestData.group(id: 10L)
        def cart = TestData.cart(group: group, user: TestData.user(id: 5L), category: TestData.category(id: 20L, group: group),
                datePurchased: DateUtils.toDate(tag))

        when:
        def dto = new CartDto(cart)
        def zurueck = mapper.cartDtoToEntity(dto, cart.category, cart.user, group, 1)

        then: 'die API überträgt den Kalendertag, und die Entity erhält wieder denselben Tag'
        dto.datePurchased == tag
        DateUtils.toLocalDate(zurueck.datePurchased) == tag

        where:
        tag << [LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1)]
    }

    def "Fehlendes Kaufdatum bleibt in beide Richtungen leer"() {
        given:
        def group = TestData.group(id: 10L)
        def cart = TestData.cart(group: group, user: TestData.user(id: 5L), category: TestData.category(id: 20L, group: group),
                datePurchased: null)

        when:
        def dto = new CartDto(cart)
        def zurueck = mapper.cartDtoToEntity(dto, cart.category, cart.user, group, 1)

        then:
        dto.datePurchased == null
        zurueck.datePurchased == null
    }
}
