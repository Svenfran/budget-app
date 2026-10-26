package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupDto
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.DatabaseCleaner
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * Zustellung nach Commit (DIV-36, P5.1) gegen eine echte Transaktion: Benachrichtigungen gehen erst nach dem
 * erfolgreichen Commit hinaus, bei Rollback oder fehlschlagendem Commit gar nicht.
 *
 * Der NotificationService im Kontext ist gemockt (IntegrationSpec); diese Spec baut daher eine echte Instanz mit
 * Mock(SimpMessagingTemplate) und den echten Beans für Mitgliedschaften und angemeldeten Nutzer (keine eigene
 * Kontext-Konfiguration). Gesendete Empfänger-IDs werden in {@code gesendet} mitgeschrieben, damit der Stand
 * innerhalb des Transaktions-Callbacks festgehalten werden kann.
 *
 * Commit-abhängig: keine Test-Transaktion, Aufräumen vor jedem Feature; Transaktionen über TransactionTemplate.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationAfterCommitIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationAfterCommitIntegrationSpec)

    @Autowired GroupMembershipHistoryService gmhService
    @Autowired DataLoaderService dataLoaderService
    @Autowired ApplicationContext applicationContext
    @Autowired PlatformTransactionManager transactionManager
    @Autowired UserRepository userRepository
    @Autowired DatabaseCleaner databaseCleaner

    def messagingTemplate = Mock(SimpMessagingTemplate)
    List<String> gesendet = []
    NotificationService service
    TransactionTemplate transaction

    def setup() {
        databaseCleaner.truncateAll()
        messagingTemplate.convertAndSendToUser(_, '/notification/update-group', _) >> { args -> gesendet << (args[0] as String) }
        service = new NotificationService(messagingTemplate, applicationContext, gmhService, dataLoaderService)
        transaction = new TransactionTemplate(transactionManager)
    }

    def "Nach erfolgreichem Commit erhalten alle Mitglieder außer dem auslösenden Nutzer die Benachrichtigung"() {
        given:
        def ben = fixtures.user()
        def cleo = fixtures.user()
        def wg = fixtures.group(members: [ben, cleo])
        def groupId = wg.group.id
        def benId = ben.id, cleoId = cleo.id, ownerId = wg.owner.id
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet, auslösend), Mitglieder {} und {}', groupId, ownerId, benId, cleoId)

        when:
        List<String> imCallback = null
        transaction.executeWithoutResult {
            service.sendGroupUpdateNotification(groupId, new GroupDto(id: groupId, name: 'WG'))
            imCallback = new ArrayList<>(gesendet)
        }
        LOG.info('sendGroupUpdateNotification(Gruppe {}) in Transaktion -> im Callback gesendet an {}, nach Commit an {}', groupId, imCallback, gesendet)

        then: "innerhalb der Transaktion nichts, nach dem Commit an beide Mitglieder"
        imCallback == []
        gesendet.sort() == [benId, cleoId]*.toString().sort()
    }

    def "Bei Rollback wird nichts gesendet"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet, auslösend), Mitglied {}', groupId, wg.owner.id, ben.id)

        when:
        LOG.info('sendGroupUpdateNotification(Gruppe {}) in Transaktion, danach wirft der Callback, erwartet IllegalStateException', groupId)
        transaction.executeWithoutResult {
            service.sendGroupUpdateNotification(groupId, new GroupDto(id: groupId, name: 'WG'))
            throw new IllegalStateException('Fehler nach der Benachrichtigung')
        }

        then:
        thrown(IllegalStateException)
        gesendet == []
    }

    def "Schlägt der Commit selbst fehl, wird nichts gesendet"() {
        given: "zwei committete Nutzer; im Callback bekommt Ben Cleos E-Mail (verletzt uk_user_details_email erst beim Flush im Commit)"
        def ben = fixtures.user()
        def cleo = fixtures.user()
        def wg = fixtures.group(members: [ben, cleo])
        def groupId = wg.group.id
        def benId = ben.id
        def cleoEmail = cleo.email
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet, auslösend), Mitglieder {} und {}; Ben bekommt im Callback Cleos E-Mail', groupId, wg.owner.id, benId, cleo.id)

        when: "kein expliziter flush() und nach der Änderung keine Query, damit der Fehler erst im Commit auftritt"
        def callbackDurchgelaufen = false
        LOG.info('sendGroupUpdateNotification(Gruppe {}) + E-Mail-Konflikt bei Nutzer {}, erwartet DataIntegrityViolationException beim Commit', groupId, benId)
        transaction.executeWithoutResult {
            service.sendGroupUpdateNotification(groupId, new GroupDto(id: groupId, name: 'WG'))
            userRepository.findById(benId).get().email = cleoEmail
            callbackDurchgelaufen = true
        }

        then:
        thrown(DataIntegrityViolationException)
        callbackDurchgelaufen
        gesendet == []
    }
}
