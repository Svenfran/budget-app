package com.github.svenfran.budgetapp.budgetappbackend.service.mapper

import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import spock.lang.Specification

class CartDtoMapperSpec extends Specification {

    def mapper = new CartDtoMapper()

    def "Cart-DTO wird mit Gruppe, Kategorie, Nutzer und Durchschnitt pro Mitglied übernommen"() {
        given:
        def dto = TestData.cartDto(id: 1L, title: 'Pizza', description: 'Freitag', amount: 55d,
                datePurchased: DateUtils.toDate(TestData.REFERENCE_DATE))
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
}
