package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

class UserProfileServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(UserProfileServiceIntegrationSpec)

    @Autowired UserProfileService userProfileService

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
}
