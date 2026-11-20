package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.constants.SupportedCurrency
import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingListDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupDto
import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.CurrencyChangeConfirmationRequiredException
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupCurrencyChangeRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.DatabaseCleaner
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import spock.util.concurrent.PollingConditions

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Währungswechsel einer Gruppe (DIV-77) über Commit-Grenzen hinweg:
 * - Ein abgelehnter Request (neuer Name + unbestätigter Wechsel) ändert nichts, auch nicht den Namen (rollbackFor).
 * - Ein fremder Schreibpfad, der die Gruppe vor dem Wechsel geladen hat und danach per save/merge speichert
 *   (updateShoppingList), schreibt die alte Währung nicht zurück (currency_code ist updatable = false).
 * - Zwei Wechsel derselben Gruppe laufen über die Zeilensperre nacheinander; das Protokoll ist eine lückenlose Kette.
 *
 * Commit-abhängig: keine Test-Transaktion, Aufräumen vor jedem Feature; Transaktionen über TransactionTemplate, jeder
 * Thread meldet sich selbst an (SecurityContext ist thread-lokal). Muster: ShoppingReorderConcurrencyIntegrationSpec.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GroupCurrencyTransactionIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(GroupCurrencyTransactionIntegrationSpec)

    /** Sicherheitstimeout in Sekunden für Latches, das Warten auf die Sperre und die Threads. */
    private static final long TIMEOUT = 20

    @Autowired GroupService groupService
    @Autowired ShoppingListService shoppingListService
    @Autowired GroupRepository groupRepository
    @Autowired GroupCurrencyChangeRepository currencyChangeRepository
    @Autowired PlatformTransactionManager transactionManager
    @Autowired JdbcTemplate jdbcTemplate
    @Autowired DatabaseCleaner databaseCleaner

    TransactionTemplate transaction

    def setup() {
        databaseCleaner.truncateAll()
        transaction = new TransactionTemplate(transactionManager)
    }

    def "Abgelehnter Wechsel mit neuem Namen ändert weder Name noch Währung und schreibt kein Protokoll"() {
        given:
        def wg = fixtures.group()
        def groupId = wg.group.id
        def cart = fixtures.cart(wg)
        def alterName = wg.group.name
        LOG.info('Szenario: Gruppe {} "{}" (EUR), Owner {}, Einkauf {}; Request: Name "Neu", USD ohne Bestätigung',
                groupId, alterName, wg.owner.id, cart.id)
        // ohne äußere Transaktion: geprüft wird die eigene Transaktion von updateGroup (rollbackFor bei checked Exceptions)
        loginAs(wg.owner)

        when:
        LOG.info('groupService.updateGroup(Gruppe {}, "Neu", USD) als Owner, erwartet CurrencyChangeConfirmationRequiredException', groupId)
        groupService.updateGroup(new GroupDto(id: groupId, name: 'Neu', currencyCode: 'USD'))

        then:
        thrown(CurrencyChangeConfirmationRequiredException)
        0 * notificationService._
        def (name, waehrung) = gruppe(groupId)
        name == alterName
        waehrung == SupportedCurrency.EUR
        currencyChangeRepository.findByGroupIdOrderByChangedAtAscIdAsc(groupId).isEmpty()
    }

    def "Einkaufsliste mit vor dem Wechsel geladener Gruppe speichern schreibt die alte Währung nicht zurück"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def liste = fixtures.shoppingList(wg)
        LOG.info('Szenario: Gruppe {} (EUR), Owner {}, Mitglied {}, Liste {}; A lädt die Gruppe, B wechselt auf USD und committet, A speichert die Liste',
                groupId, wg.owner.id, ben.id, liste.id)
        def geladen = new CountDownLatch(1)
        def gewechselt = new CountDownLatch(1)
        def pool = Executors.newSingleThreadExecutor()

        when:
        def a = pool.submit({
            inTransaction(ben) {
                def waehrungBeimLaden = groupRepository.findById(groupId).get().currencyCode
                geladen.countDown()
                assert gewechselt.await(TIMEOUT, TimeUnit.SECONDS)
                shoppingListService.updateShoppingList(new AddEditShoppingListDto(id: liste.id, name: 'Neu', groupId: groupId))
                waehrungBeimLaden
            }
        } as Callable)
        assert geladen.await(TIMEOUT, TimeUnit.SECONDS)
        inTransaction(wg.owner) { groupService.updateGroup(new GroupDto(id: groupId, name: wg.group.name, currencyCode: 'USD')) }
        gewechselt.countDown()
        def waehrungInA = a.get(TIMEOUT, TimeUnit.SECONDS)
        pool.shutdownNow()
        def (name, waehrung) = gruppe(groupId)
        LOG.info('A (Währung beim Laden {}) und B committet -> gespeicherte Währung {}', waehrungInA, waehrung)

        then:
        waehrungInA == SupportedCurrency.EUR
        waehrung == SupportedCurrency.USD
    }

    def "Zwei gleichzeitige Wechsel laufen nacheinander, das Protokoll ist die Kette EUR→USD, USD→CHF"() {
        given:
        def wg = fixtures.group()
        def groupId = wg.group.id
        def ownerId = wg.owner.id
        def name = wg.group.name
        LOG.info('Szenario: Gruppe {} (EUR, ohne Einkäufe), Owner {}; T1 wechselt auf USD und hält offen, T2 wechselt auf CHF', groupId, ownerId)
        def ersterAusgefuehrt = new CountDownLatch(1)
        def commitFreigabe = new CountDownLatch(1)
        def pool = Executors.newFixedThreadPool(2)

        when:
        def t1 = pool.submit({
            inTransaction(wg.owner) {
                def result = groupService.updateGroup(new GroupDto(id: groupId, name: name, currencyCode: 'USD'))
                ersterAusgefuehrt.countDown()
                assert commitFreigabe.await(TIMEOUT, TimeUnit.SECONDS)
                result
            }
        } as Callable)
        assert ersterAusgefuehrt.await(TIMEOUT, TimeUnit.SECONDS)
        def t2 = pool.submit({
            inTransaction(wg.owner) { groupService.updateGroup(new GroupDto(id: groupId, name: name, currencyCode: 'CHF')) }
        } as Callable)
        new PollingConditions(timeout: TIMEOUT).eventually {
            assert wartendAufSperre() == 1
        }
        commitFreigabe.countDown()
        def erste = t1.get(TIMEOUT, TimeUnit.SECONDS)
        def zweite = t2.get(TIMEOUT, TimeUnit.SECONDS)
        pool.shutdownNow()
        def protokoll = currencyChangeRepository.findByGroupIdOrderByChangedAtAscIdAsc(groupId)
                .collect { [it.oldCurrencyCode.name(), it.newCurrencyCode.name(), it.changedByUserId] }
        def (gespeicherterName, waehrung) = gruppe(groupId)
        LOG.info('T1 -> {}, T2 -> {}; gespeichert {}, Protokoll {}', erste.currencyCode, zweite.currencyCode, waehrung, protokoll)

        then:
        erste.currencyCode == 'USD'
        zweite.currencyCode == 'CHF'
        waehrung == SupportedCurrency.CHF
        protokoll == [['EUR', 'USD', ownerId], ['USD', 'CHF', ownerId]]
    }

    /** Name und Währung der Gruppe aus der Datenbank (eigene Transaktion, alles committet). */
    private List gruppe(Long groupId) {
        jdbcTemplate.queryForMap('select name, currency_code from group_details where id = ?', groupId)
                .with { [it.name, SupportedCurrency.valueOf(it.currency_code as String)] }
    }

    private Object inTransaction(User user, Closure aufruf) {
        loginAs(user)
        try {
            transaction.execute { aufruf.call() }
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    /** Zahl der Datenbankverbindungen, die gerade auf eine Sperre warten. */
    private int wartendAufSperre() {
        jdbcTemplate.queryForObject(
                "select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'",
                Integer)
    }
}
