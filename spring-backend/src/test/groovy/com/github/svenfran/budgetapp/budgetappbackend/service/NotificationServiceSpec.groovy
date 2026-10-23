package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupMembersDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.springframework.context.ApplicationContext
import org.springframework.messaging.simp.SimpMessagingTemplate
import spock.lang.Specification

/**
 * WebSocket-Vertrag von NotificationService (ohne Spring): Topic, Empfänger und Payload je send*-Methode.
 *
 * Szenario: Gruppe 7, angemeldeter (auslösender) Nutzer 1, weitere aktive Mitglieder 2 und 3. Empfänger-IDs
 * gehen als String an convertAndSendToUser; der auslösende Nutzer erhält nie eine Nachricht.
 * Topics stehen als Literale im Test – sie sind der Vertrag mit den Subscriptions im Frontend.
 * DTOs ohne equals werden per Referenz verglichen, d. h. der Payload wird unverändert durchgereicht.
 *
 * Ob die fachlichen Services die Benachrichtigungen auslösen, prüft diese Spec nicht (Konzept 3.8, Ebene 1).
 */
class NotificationServiceSpec extends Specification {

    static final Long GRUPPE_ID = 7L

    def messagingTemplate = Mock(SimpMessagingTemplate)
    def applicationContext = Mock(ApplicationContext)
    def gmhService = Mock(GroupMembershipHistoryService)
    def dataLoaderService = Stub(DataLoaderService) {
        getAuthenticatedUser() >> TestData.user(id: 1L)
    }
    def service = new NotificationService(messagingTemplate, applicationContext)

    def aktiveMitglieder = [1L, 2L, 3L].collect { TestData.membership(userId: it, groupId: GRUPPE_ID) }

    def setup() {
        service.gmhService = gmhService
        service.dataLoaderService = dataLoaderService
    }

    def "Gruppen-Update geht an alle aktiven Mitglieder außer dem auslösenden Nutzer"() {
        given:
        def dto = new GroupDto(id: GRUPPE_ID, name: 'WG')

        when:
        service.sendGroupUpdateNotification(GRUPPE_ID, dto)

        then:
        1 * gmhService.getGroupMembersAndOwner(GRUPPE_ID) >> aktiveMitglieder
        1 * messagingTemplate.convertAndSendToUser('2', '/notification/update-group', dto)
        1 * messagingTemplate.convertAndSendToUser('3', '/notification/update-group', dto)
        0 * messagingTemplate.convertAndSendToUser('1', _, _)
        0 * messagingTemplate._
    }

    def "Neues Mitglied: Benachrichtigung an alle aktiven Mitglieder außer dem auslösenden Nutzer"() {
        given: "Mitglied 3 wurde gerade hinzugefügt und ist bereits aktiv"
        def dto = new GroupMembersDto(id: GRUPPE_ID, updatedMember: new UserDto(TestData.user(id: 3L)))

        when:
        service.sendGroupMemberAddedNotification(GRUPPE_ID, dto)

        then:
        1 * gmhService.getGroupMembersAndOwner(GRUPPE_ID) >> aktiveMitglieder
        1 * messagingTemplate.convertAndSendToUser('2', '/notification/add-group-member', dto)
        1 * messagingTemplate.convertAndSendToUser('3', '/notification/add-group-member', dto)
        0 * messagingTemplate.convertAndSendToUser('1', _, _)
        0 * messagingTemplate._
    }

    def "Ownerwechsel geht mit neuem Owner und Gruppe an alle außer dem auslösenden Nutzer"() {
        given: "Owner 1 übergibt an Mitglied 3; das Frontend übernimmt newOwner.userName als ownerName"
        def neuerOwner = TestData.user(id: 3L, name: 'Neuer Owner')

        when:
        service.sendGroupOwnerChangedNotification(GRUPPE_ID, neuerOwner)

        then:
        1 * gmhService.getGroupMembersAndOwner(GRUPPE_ID) >> aktiveMitglieder
        1 * messagingTemplate.convertAndSendToUser('2', '/notification/change-group-owner', {
            it.groupId == GRUPPE_ID && it.newOwner.id == 3L && it.newOwner.userName == 'Neuer Owner'
        })
        1 * messagingTemplate.convertAndSendToUser('3', '/notification/change-group-owner', {
            it.groupId == GRUPPE_ID && it.newOwner.id == 3L && it.newOwner.userName == 'Neuer Owner'
        })
        0 * messagingTemplate.convertAndSendToUser('1', _, _)
        0 * messagingTemplate._
    }

    def "Gruppe gelöscht: Empfänger kommen aus der übergebenen Mitgliedschaftsliste"() {
        given: "GroupService lädt die Mitgliedschaften vor dem Löschen und übergibt sie"
        def dto = new GroupDto(id: GRUPPE_ID, name: 'WG')

        when:
        service.sendGroupDeletedNotification(aktiveMitglieder, dto)

        then:
        0 * gmhService._
        1 * messagingTemplate.convertAndSendToUser('2', '/notification/delete-group', dto)
        1 * messagingTemplate.convertAndSendToUser('3', '/notification/delete-group', dto)
        0 * messagingTemplate.convertAndSendToUser('1', _, _)
        0 * messagingTemplate._
    }

    def "Mitglied entfernt: auch das entfernte Mitglied aus der übergebenen Liste wird benachrichtigt"() {
        given: "Owner 1 entfernt Mitglied 3; die Liste stammt von vor der Entfernung und enthält 3 noch"
        def dto = new GroupMembersDto(id: GRUPPE_ID, updatedMember: new UserDto(TestData.user(id: 3L)))

        when:
        service.sendGroupMemberRemovedNotification(aktiveMitglieder, dto)

        then:
        0 * gmhService._
        1 * messagingTemplate.convertAndSendToUser('2', '/notification/remove-group-member', dto)
        1 * messagingTemplate.convertAndSendToUser('3', '/notification/remove-group-member', dto)  // entferntes Mitglied
        0 * messagingTemplate.convertAndSendToUser('1', _, _)
        0 * messagingTemplate._
    }
}
