package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddGroupMemberDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.ChangeGroupOwnerDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.RemoveGroupMemberDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto
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

    // Auslösung der Benachrichtigungen (Konzept 3.8, Ebene 1). Topic, Empfänger und Payload prüft NotificationServiceSpec.

    def "Gruppe umbenennen löst update-group mit dem neuen Namen aus"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet), Mitglied {}; neuer Name "Neue WG"', groupId, wg.owner.id, ben.id)

        when:
        def updated = groupService.updateGroup(new GroupDto(id: groupId, name: 'Neue WG'))
        LOG.info('groupService.updateGroup(Gruppe {}, name "Neue WG") -> name "{}"', groupId, updated.name)

        then:
        1 * notificationService.sendGroupUpdateNotification(groupId, { it.id == groupId && it.name == 'Neue WG' })
        0 * notificationService._
    }

    def "Mitglied hinzufügen löst add-group-member mit dem neuen Mitglied aus"() {
        given:
        def wg = fixtures.group()
        def ben = fixtures.user()
        def groupId = wg.group.id
        def benId = ben.id
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet), neues Mitglied {}', groupId, wg.owner.id, benId)

        when:
        def result = groupService.addMemberToGroup(new AddGroupMemberDto(id: groupId, newMemberEmail: ben.email))
        LOG.info('groupService.addMemberToGroup(Gruppe {}, Nutzer {}) -> updatedMember {}', groupId, benId, result.updatedMember.id)

        then:
        1 * notificationService.sendGroupMemberAddedNotification(groupId, { it.id == groupId && it.updatedMember.id == benId })
        0 * notificationService._
    }

    def "Mitglied entfernen löst remove-group-member mit der Mitgliederliste von vor der Entfernung aus"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def ownerId = wg.owner.id
        def benId = ben.id
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet) entfernt Mitglied {}', groupId, ownerId, benId)

        when:
        def result = groupService.removeMemberFromGroup(new RemoveGroupMemberDto(id: groupId, member: new UserDto(id: benId)))
        LOG.info('groupService.removeMemberFromGroup(Gruppe {}, Nutzer {}) -> updatedMember {}', groupId, benId, result.updatedMember.id)

        then: "die Liste enthält das entfernte Mitglied, damit dessen Client die Gruppe verwirft"
        1 * notificationService.sendGroupMemberRemovedNotification(
                { it*.userId as Set == [ownerId, benId] as Set },
                { it.id == groupId && it.updatedMember.id == benId })
        0 * notificationService._
    }

    def "Gruppe löschen löst delete-group mit den aktiven Mitgliedern aus"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def ownerId = wg.owner.id
        def benId = ben.id
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet), Mitglied {}', groupId, ownerId, benId)

        when:
        groupService.deleteGroup(groupId)
        LOG.info('groupService.deleteGroup(Gruppe {}) aufgerufen', groupId)

        then:
        1 * notificationService.sendGroupDeletedNotification(
                { it*.userId as Set == [ownerId, benId] as Set },
                { it.id == groupId })
        0 * notificationService._
    }

    def "Ownerwechsel löst change-group-owner mit dem neuen Owner aus"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def benId = ben.id
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet) übergibt an Mitglied {}', groupId, wg.owner.id, benId)

        when:
        groupService.changeGroupOwner(new ChangeGroupOwnerDto(new UserDto(id: benId), groupId))
        LOG.info('groupService.changeGroupOwner(Gruppe {}, neuer Owner {}) aufgerufen', groupId, benId)

        then:
        1 * notificationService.sendGroupOwnerChangedNotification(groupId, { it.id == benId })
        0 * notificationService._
    }
}
