package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.springframework.beans.factory.annotation.Autowired

import static com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData.REFERENCE_DATE

class GroupServiceIntegrationSpec extends IntegrationSpec {

    @Autowired GroupService groupService
    @Autowired GroupRepository groupRepository
    @Autowired CartRepository cartRepository

    def "Neuberechnung setzt den Durchschnitt pro Mitglied auf 0, wenn zum Kaufdatum niemand Mitglied ist"() {
        given: "Owner und Ben sind seit REFERENCE_DATE - 1 Jahr Mitglied"
        def wg = fixtures.group(members: [fixtures.user()])
        def imZeitraum = fixtures.cart(wg, amount: 60d, datePurchased: DateUtils.toDate(REFERENCE_DATE))
        def vorZeitraum = fixtures.cart(wg, amount: 40d, datePurchased: DateUtils.toDate(REFERENCE_DATE.minusYears(2)))
        flushAndClear()

        when:
        groupService.calculateAveragePerMember(groupRepository.findById(wg.group.id).get())
        flushAndClear()

        then:
        cartRepository.findById(imZeitraum.id).get().averagePerMember == 30d
        cartRepository.findById(vorZeitraum.id).get().averagePerMember == 0d
    }
}
