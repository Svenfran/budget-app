package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingListDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupMembersDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthEndpoint
import org.springframework.context.ApplicationContext
import org.springframework.messaging.MessageDeliveryException
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
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
 *
 * Ohne aktive Transaktions-Synchronisation wird sofort gesendet (alle Features außer den „Transaktion“-Features).
 * Die „Transaktion“-Features simulieren die Synchronisation über TransactionSynchronizationManager und rufen
 * afterCommit/afterCompletion selbst auf; gegen eine echte Transaktion prüft das NotificationAfterCommitIntegrationSpec.
 */
class NotificationServiceSpec extends Specification {

    static final Long GRUPPE_ID = 7L

    def messagingTemplate = Mock(SimpMessagingTemplate)
    def applicationContext = Mock(ApplicationContext)
    def gmhService = Mock(GroupMembershipHistoryService)
    def dataLoaderService = Mock(DataLoaderService) {
        getAuthenticatedUser() >> TestData.user(id: 1L)
    }
    def service = new NotificationService(messagingTemplate, applicationContext, gmhService, dataLoaderService)

    def aktiveMitglieder = [1L, 2L, 3L].collect { TestData.membership(userId: it, groupId: GRUPPE_ID) }

    def cleanup() {
        if (TransactionSynchronizationManager.synchronizationActive) {
            TransactionSynchronizationManager.clearSynchronization()
        }
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

    def "Einkaufsliste #action geht auf /notification/#action-list an alle außer dem auslösenden Nutzer"() {
        given:
        def dto = new AddEditShoppingListDto(id: 5L, name: 'Wocheneinkauf', groupId: GRUPPE_ID)

        when:
        service.sendShoppingListNotification(GRUPPE_ID, dto, action)

        then:
        1 * gmhService.getGroupMembersAndOwner(GRUPPE_ID) >> aktiveMitglieder
        1 * messagingTemplate.convertAndSendToUser('2', topic, dto)
        1 * messagingTemplate.convertAndSendToUser('3', topic, dto)
        0 * messagingTemplate.convertAndSendToUser('1', _, _)
        0 * messagingTemplate._

        where:
        action   || topic
        'add'    || '/notification/add-list'
        'update' || '/notification/update-list'
        'delete' || '/notification/delete-list'
    }

    def "Eintrag #action geht auf /notification/#action-item an alle außer dem auslösenden Nutzer"() {
        given:
        def dto = new AddEditShoppingItemDto(id: 11L, name: 'Milch', shoppingListId: 5L, groupId: GRUPPE_ID)

        when:
        service.sendShoppingItemNotification(GRUPPE_ID, dto, action)

        then:
        1 * gmhService.getGroupMembersAndOwner(GRUPPE_ID) >> aktiveMitglieder
        1 * messagingTemplate.convertAndSendToUser('2', topic, dto)
        1 * messagingTemplate.convertAndSendToUser('3', topic, dto)
        0 * messagingTemplate.convertAndSendToUser('1', _, _)
        0 * messagingTemplate._

        where:
        action   || topic
        'add'    || '/notification/add-item'
        'update' || '/notification/update-item'
        'delete' || '/notification/delete-item'
    }

    def "Alle erledigten Einträge gelöscht: Liste der DTOs geht auf delete-all-items an alle außer dem auslösenden Nutzer"() {
        given: "zwei unterschiedliche Einträge, damit eine gekürzte oder umsortierte Liste auffällt"
        def milch = new AddEditShoppingItemDto(id: 11L, name: 'Milch', shoppingListId: 5L, groupId: GRUPPE_ID)
        def brot = new AddEditShoppingItemDto(id: 12L, name: 'Brot', shoppingListId: 5L, groupId: GRUPPE_ID)

        when:
        service.sendShoppingItemDeleteAllNotification(GRUPPE_ID, [milch, brot])

        then: "Listen werden nach Inhalt und Reihenfolge verglichen, die Einträge selbst per Referenz"
        1 * gmhService.getGroupMembersAndOwner(GRUPPE_ID) >> aktiveMitglieder
        1 * messagingTemplate.convertAndSendToUser('2', '/notification/delete-all-items', [milch, brot])
        1 * messagingTemplate.convertAndSendToUser('3', '/notification/delete-all-items', [milch, brot])
        0 * messagingTemplate.convertAndSendToUser('1', _, _)
        0 * messagingTemplate._
    }

    def "Leere Liste bei delete-all-items sendet nichts und wirft nicht"() {
        // Regression: Früher las das Logging dtos.get(0) und warf eine IndexOutOfBoundsException.
        when:
        service.sendShoppingItemDeleteAllNotification(GRUPPE_ID, [])

        then:
        noExceptionThrown()
        0 * gmhService._
        0 * messagingTemplate._
    }

    def "Health-Status wird als Broadcast auf /notification/health gesendet, nur mit dem Status"() {
        given:
        def healthEndpoint = Mock(HealthEndpoint)
        def health = Health.up().withDetail('db', 'PostgreSQL').build()

        when:
        service.sendHealthStatus()

        then:
        1 * applicationContext.getBean(HealthEndpoint) >> healthEndpoint
        1 * healthEndpoint.health() >> health
        1 * messagingTemplate.convertAndSend('/notification/health', [status: 'UP'])
        0 * messagingTemplate._
    }

    def "Transaktion: Empfänger werden beim Aufruf ermittelt, gesendet wird erst nach dem Commit"() {
        given: "eine aktive Transaktion"
        TransactionSynchronizationManager.initSynchronization()
        def dto = new GroupDto(id: GRUPPE_ID, name: 'WG')

        when:
        service.sendGroupUpdateNotification(GRUPPE_ID, dto)

        then: "Empfänger und auslösender Nutzer sind ermittelt, eine Synchronisation registriert, noch nichts gesendet"
        1 * dataLoaderService.getAuthenticatedUser() >> TestData.user(id: 1L)
        1 * gmhService.getGroupMembersAndOwner(GRUPPE_ID) >> aktiveMitglieder
        0 * messagingTemplate._
        TransactionSynchronizationManager.synchronizations.size() == 1

        when: "die Transaktion committet"
        commit()

        then: "erst jetzt wird gesendet, ohne erneutes Laden"
        0 * dataLoaderService._
        0 * gmhService._
        1 * messagingTemplate.convertAndSendToUser('2', '/notification/update-group', dto)
        1 * messagingTemplate.convertAndSendToUser('3', '/notification/update-group', dto)
        0 * messagingTemplate.convertAndSendToUser('1', _, _)
        0 * messagingTemplate._
    }

    def "Transaktion: bei Rollback wird nichts gesendet"() {
        given: "eine aktive Transaktion"
        TransactionSynchronizationManager.initSynchronization()
        def dto = new AddEditShoppingListDto(id: 5L, name: 'Wocheneinkauf', groupId: GRUPPE_ID)

        when: "die Transaktion wird nach dem Aufruf zurückgerollt"
        service.sendShoppingListNotification(GRUPPE_ID, dto, 'add')
        TransactionSynchronizationManager.synchronizations*.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)

        then:
        1 * gmhService.getGroupMembersAndOwner(GRUPPE_ID) >> aktiveMitglieder
        0 * messagingTemplate._
    }

    def "Transaktion: ein Sendefehler nach dem Commit erreicht den Aufrufer nicht, die übrigen Empfänger erhalten die Nachricht"() {
        given: "eine aktive Transaktion"
        TransactionSynchronizationManager.initSynchronization()
        def dto = new GroupDto(id: GRUPPE_ID, name: 'WG')

        when: "die Transaktion committet und das Senden an Mitglied 2 schlägt fehl"
        service.sendGroupUpdateNotification(GRUPPE_ID, dto)
        commit()

        then:
        1 * gmhService.getGroupMembersAndOwner(GRUPPE_ID) >> aktiveMitglieder
        1 * messagingTemplate.convertAndSendToUser('2', '/notification/update-group', dto) >> {
            throw new MessageDeliveryException('Broker nicht erreichbar')
        }
        1 * messagingTemplate.convertAndSendToUser('3', '/notification/update-group', dto)
        noExceptionThrown()
    }

    private static void commit() {
        TransactionSynchronizationManager.synchronizations*.afterCommit()
    }
}
