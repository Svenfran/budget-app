package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

import static com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData.REFERENCE_DATE

class GroupServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(GroupServiceIntegrationSpec)

    @Autowired GroupService groupService
    @Autowired GroupRepository groupRepository
    @Autowired CartRepository cartRepository

    def "Neuberechnung setzt den Durchschnitt pro Mitglied auf 0, wenn zum Kaufdatum niemand Mitglied ist"() {
        given: "Owner und Ben sind seit REFERENCE_DATE - 1 Jahr Mitglied"
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def imZeitraum = fixtures.cart(wg, amount: 60d, datePurchased: DateUtils.toDate(REFERENCE_DATE))
        def vorZeitraum = fixtures.cart(wg, amount: 40d, datePurchased: DateUtils.toDate(REFERENCE_DATE.minusYears(2)))
        flushAndClear()
        LOG.info('Szenario: Gruppe {}, Owner {}, Ben {} (Mitglieder ab {}); Einkauf {} 60.00 am {}, Einkauf {} 40.00 am {}',
                wg.group.id, wg.owner.id, ben.id, REFERENCE_DATE.minusYears(1), imZeitraum.id, REFERENCE_DATE,
                vorZeitraum.id, REFERENCE_DATE.minusYears(2))

        when:
        groupService.calculateAveragePerMember(groupRepository.findById(wg.group.id).get())
        flushAndClear()
        def averageImZeitraum = cartRepository.findById(imZeitraum.id).get().averagePerMember
        def averageVorZeitraum = cartRepository.findById(vorZeitraum.id).get().averagePerMember
        LOG.info('groupService.calculateAveragePerMember(Gruppe {}) -> averagePerMember Einkauf {}: {}, Einkauf {}: {}',
                wg.group.id, imZeitraum.id, averageImZeitraum, vorZeitraum.id, averageVorZeitraum)

        then:
        averageImZeitraum == 30d
        averageVorZeitraum == 0d
    }
}
