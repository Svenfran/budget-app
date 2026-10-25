package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.repository.CartTemplateRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

import java.time.LocalDate

class UserProfileServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(UserProfileServiceIntegrationSpec)

    @Autowired UserProfileService userProfileService
    @Autowired CartTemplateRepository cartTemplateRepository

    def "Konto löschen als Owner löst delete-group für die eigene Gruppe mit den aktiven Mitgliedern aus"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def ownerId = wg.owner.id
        def benId = ben.id
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet) löscht sein Konto, Mitglied {}', groupId, ownerId, benId)

        when:
        userProfileService.deleteUserProfile(ownerId)
        LOG.info('userProfileService.deleteUserProfile(Nutzer {}) aufgerufen', ownerId)

        then:
        1 * notificationService.sendGroupDeletedNotification(
                { it*.userId as Set == [ownerId, benId] as Set },
                { it.id == groupId })
        0 * notificationService._
    }

    def "Konto löschen als Mitglied beendet seine aktiven Templates der Gruppe zum heutigen Tag (testClock)"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def miete = fixtures.cart(wg, user: ben, title: 'Miete')
        def template = fixtures.template(miete)
        testClock.today = LocalDate.of(2025, 7, 1)
        def groupId = wg.group.id
        def benId = ben.id
        def templateId = template.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: heute {}, Gruppe {}, Owner {}, Mitglied {} (angemeldet) löscht sein Konto; aktives Template {} von Ben',
                testClock.today, groupId, wg.owner.id, benId, templateId)

        when:
        userProfileService.deleteUserProfile(benId)
        flushAndClear()
        def gespeichert = cartTemplateRepository.findById(templateId).get()
        LOG.info('userProfileService.deleteUserProfile(Nutzer {}) -> Template {} active {}, endDate {}',
                benId, templateId, gespeichert.active, gespeichert.endDate)

        then:
        !gespeichert.active
        gespeichert.endDate == LocalDate.of(2025, 7, 1)
    }
}
