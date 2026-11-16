package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.RemoveGroupMemberDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.SettlementPaymentDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto
import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.SettlementNotFoundException
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.DatabaseCleaner
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder
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
 * Transaktionsgrenzen und Nebenläufigkeit der Ausgleichszahlungen (DIV-74, AC6/AC7).
 *
 * - Commit-abhängig: keine Test-Transaktion, damit eine fehlende Service-Transaktion nicht von der Test-Transaktion
 *   verdeckt wird; Aufräumen vor jedem Feature.
 * - Atomarität: Der Fehler entsteht über einen spec-lokalen Postgres-Trigger erst nach einem tatsächlich ausgeführten
 *   SQL-Write. Der Trigger wird im cleanup:-Block in jedem Fall wieder entfernt.
 * - Nebenläufigkeit (Muster ShoppingReorderConcurrencyIntegrationSpec): Transaktion 1 hält offen, Transaktion 2 startet
 *   in einem zweiten Thread und muss an der Settlement-Sperre warten (geprüft über pg_stat_activity, ohne sleep); danach
 *   committet 1, dann 2. Jeder Thread meldet sich selbst an (SecurityContext ist thread-lokal).
 * - Geprüft wird mit neuen Abfragen über JdbcTemplate (eigene, frische Verbindung/Transaktion).
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SettlementTransactionIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(SettlementTransactionIntegrationSpec)

    private static final String TRIGGER_MELDUNG = 'div74-test: Write abgelehnt'

    @Autowired SettlementService settlementService
    @Autowired CartService cartService
    @Autowired JdbcTemplate jdbcTemplate
    @Autowired DatabaseCleaner databaseCleaner
    @Autowired GroupService groupService
    @Autowired PlatformTransactionManager transactionManager

    /** Sicherheitstimeout in Sekunden für Latches, das Warten auf die Sperre und die Threads. */
    private static final long TIMEOUT = 20

    TransactionTemplate transaction

    def setup() {
        databaseCleaner.truncateAll()
        LocaleContextHolder.setLocale(Locale.GERMAN)
        transaction = new TransactionTemplate(transactionManager)
    }

    def cleanup() {
        LocaleContextHolder.resetLocaleContext()
    }

    def "Anlegen: Fehler beim Empfänger-Cart nach geschriebenem Settlement und Zahler-Cart hinterlässt nichts"() {
        given: "Trigger lehnt den Insert des Empfänger-Carts ab; Settlement, Settlement-Kategorie und Zahler-Cart sind dann schon per INSERT geschrieben"
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        loginAs(wg.owner)
        installiereTrigger('cart', 'BEFORE INSERT', 'NEW.settlement_id IS NOT NULL AND NEW.amount < 0')
        def dto = new SettlementPaymentDto(30d, groupId, new UserDto(id: ben.id), DateUtils.toDate(TestData.REFERENCE_DATE))
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet) zahlt 30.00 an Ben {}; Trigger lehnt Insert des Empfänger-Carts ab',
                groupId, wg.owner.id, ben.id)

        when:
        LOG.info('settlementService.addSettlementPayment(30.00 an User {}), erwartet Abbruch durch den Trigger', ben.id)
        settlementService.addSettlementPayment(dto)

        then:
        def e = thrown(Exception)
        ursache(e).contains(TRIGGER_MELDUNG)

        and: "aus neuer Transaktion: weder Settlement noch Einkauf noch gekennzeichnete Kategorie"
        zaehle('select count(*) from settlement where group_id = ?', groupId) == 0
        zaehle('select count(*) from cart where group_id = ?', groupId) == 0
        zaehle('select count(*) from category where group_id = ? and is_settlement', groupId) == 0

        cleanup:
        entferneTrigger()
    }

    def "Löschen: Fehler beim Settlement-Delete nach gelöschten Einkäufen hinterlässt das Paar vollständig"() {
        given: "Trigger lehnt das Löschen des Settlements ab; beide Cart-Deletes laufen wegen fk_cart_settlement vorher"
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        loginAs(wg.owner)
        def paar = settlementService.addSettlementPayment(
                new SettlementPaymentDto(30d, groupId, new UserDto(id: ben.id), DateUtils.toDate(TestData.REFERENCE_DATE)))
        def settlementId = paar[0].settlementId
        installiereTrigger('settlement', 'BEFORE DELETE', 'true')
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet) zahlte an Ben {}; Settlement {} mit Carts {}; Trigger lehnt Settlement-Delete ab',
                groupId, wg.owner.id, ben.id, settlementId, paar*.id)

        when:
        LOG.info('settlementService.deleteSettlementPayment({}), erwartet Abbruch durch den Trigger', settlementId)
        settlementService.deleteSettlementPayment(settlementId)

        then:
        def e = thrown(Exception)
        ursache(e).contains(TRIGGER_MELDUNG)

        and: "aus neuer Transaktion: Settlement und beide Einkäufe vorhanden"
        zaehle('select count(*) from settlement where id = ?', settlementId) == 1
        zaehle('select count(*) from cart where settlement_id = ?', settlementId) == 2

        cleanup:
        entferneTrigger('settlement')
    }

    def "Löschen: ohne Fehler sind Settlement und beide Einkäufe nach dem Commit weg"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        loginAs(wg.owner)
        def paar = settlementService.addSettlementPayment(
                new SettlementPaymentDto(30d, groupId, new UserDto(id: ben.id), DateUtils.toDate(TestData.REFERENCE_DATE)))
        def settlementId = paar[0].settlementId
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet) zahlte an Ben {}; Settlement {} mit Carts {}', groupId,
                wg.owner.id, ben.id, settlementId, paar*.id)

        when:
        settlementService.deleteSettlementPayment(settlementId)
        def settlements = zaehle('select count(*) from settlement where id = ?', settlementId)
        def carts = zaehle('select count(*) from cart where group_id = ?', groupId)
        LOG.info('settlementService.deleteSettlementPayment({}) -> Settlements {}, Carts der Gruppe {}', settlementId, settlements, carts)

        then:
        settlements == 0
        carts == 0
    }

    def "deleteCart ist transaktional: Fehler beim Cart-Delete lässt das Template aktiv, ohne Fehler wird es deaktiviert"() {
        given: "Einziger Einkauf eines aktiven Templates; mit Trigger scheitert der Cart-Delete nach der Template-Deaktivierung"
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Miete', amount: 500d)
        def template = fixtures.template(cart)
        def (cartId, templateId) = [cart.id, template.id]
        loginAs(wg.owner)
        if (mitFehler) {
            installiereTrigger('cart', 'BEFORE DELETE', 'true')
        }
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet), Einkauf {} mit aktivem Template {}; Trigger auf Cart-Delete: {}',
                wg.group.id, wg.owner.id, cartId, templateId, mitFehler)

        when:
        def fehler = null
        try {
            cartService.deleteCart(cartId)
        } catch (Exception e) {
            fehler = ursache(e)
        }
        def cartVorhanden = zaehle('select count(*) from cart where id = ?', cartId) == 1
        def templateAktiv = jdbcTemplate.queryForObject('select active from cart_template where id = ?', Boolean, templateId)
        LOG.info('cartService.deleteCart({}) -> Fehler {}, Einkauf vorhanden {}, Template aktiv {}', cartId, fehler, cartVorhanden,
                templateAktiv)

        then:
        (fehler?.contains(TRIGGER_MELDUNG) ?: false) == mitFehler
        cartVorhanden == mitFehler
        templateAktiv == mitFehler

        cleanup:
        entferneTrigger('cart')

        where:
        mitFehler << [true, false]
    }

    // ----- Bearbeiten (Phase 3) -----

    def "Bearbeiten: Fehler beim zweiten Cart-Update nach bereits geschriebenem erstem Update hinterlässt das Paar unverändert"() {
        given: "Hibernate schreibt Settlement und Carts erst beim Flush in nicht festgelegter Reihenfolge; der Trigger zählt die Cart-Updates und wirft beim zweiten"
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        loginAs(wg.owner)
        def paar = settlementService.addSettlementPayment(
                new SettlementPaymentDto(30d, groupId, new UserDto(id: ben.id), DateUtils.toDate(TestData.REFERENCE_DATE)))
        def settlementId = paar[0].settlementId
        installiereZaehlTrigger()
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet) zahlte 30.00 an Ben {}; Settlement {} mit Carts {}; Trigger wirft beim 2. Cart-Update',
                groupId, wg.owner.id, ben.id, settlementId, paar*.id)

        when:
        LOG.info('settlementService.updateSettlementPayment({} -> 45.00), erwartet Abbruch durch den Trigger', settlementId)
        settlementService.updateSettlementPayment(aenderung(settlementId, 45d, ben.id))

        then:
        def e = thrown(Exception)
        ursache(e).contains(TRIGGER_MELDUNG)

        and: "aus neuer Transaktion: Settlement und beide Einkäufe im Ausgangszustand"
        zaehle('select count(*) from settlement where id = ? and amount = 30', settlementId) == 1
        jdbcTemplate.queryForList('select amount from cart where settlement_id = ? order by id', Double, settlementId) == [30d, -30d]

        cleanup:
        entferneTrigger('cart')
    }

    def "Zwei gleichzeitige Änderungen desselben Settlements: die zweite wartet an der Sperre, gespeichert ist sie vollständig"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        loginAs(wg.owner)
        def paar = settlementService.addSettlementPayment(
                new SettlementPaymentDto(30d, wg.group.id, new UserDto(id: ben.id), DateUtils.toDate(TestData.REFERENCE_DATE)))
        def settlementId = paar[0].settlementId
        SecurityContextHolder.clearContext()
        LOG.info('Szenario: Gruppe {}, Settlement {} (Owner {} -> Ben {}, 30.00); T1 Owner -> 40.00 hält offen, T2 Ben -> 50.00 am Vortag',
                wg.group.id, settlementId, wg.owner.id, ben.id)

        when:
        def (erste, zweite) = overlapping(wg.owner, ben,
                { settlementService.updateSettlementPayment(aenderung(settlementId, 40d, ben.id)) },
                { settlementService.updateSettlementPayment(aenderung(settlementId, 50d, ben.id, TestData.REFERENCE_DATE.minusDays(1))) })
        def gespeichert = settlementZeile(settlementId)
        def carts = cartZeilen(settlementId)
        LOG.info('T1 -> {}; T2 -> {}; gespeichert Settlement {}, Carts {}', erste*.amount, zweite*.amount, gespeichert, carts)

        then:
        erste*.amount == [40d, -40d]
        zweite*.amount == [50d, -50d]
        gespeichert.amount == 50d
        gespeichert.date_purchased.toString() == TestData.REFERENCE_DATE.minusDays(1).toString()
        carts*.amount == [50d, -50d]
        carts*.date_purchased*.toString() == [TestData.REFERENCE_DATE.minusDays(1).toString()] * 2
    }

    def "Änderung nach gleichzeitigem Löschen: wartet an der Sperre und meldet SettlementNotFoundException"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        loginAs(wg.owner)
        def paar = settlementService.addSettlementPayment(
                new SettlementPaymentDto(30d, wg.group.id, new UserDto(id: ben.id), DateUtils.toDate(TestData.REFERENCE_DATE)))
        def settlementId = paar[0].settlementId
        SecurityContextHolder.clearContext()
        LOG.info('Szenario: Gruppe {}, Settlement {}; T1 Owner {} löscht und hält offen, T2 Ben {} ändert auf 50.00', wg.group.id,
                settlementId, wg.owner.id, ben.id)

        when:
        def (erste, zweite) = overlapping(wg.owner, ben,
                { settlementService.deleteSettlementPayment(settlementId); 'gelöscht' },
                { abgefangen { settlementService.updateSettlementPayment(aenderung(settlementId, 50d, ben.id)) } })
        def settlements = zaehle('select count(*) from settlement where id = ?', settlementId)
        def carts = zaehle('select count(*) from cart where group_id = ?', wg.group.id)
        LOG.info('T1 -> {}; T2 -> {}; Settlements {}, Carts {}', erste, zweite?.class?.simpleName, settlements, carts)

        then:
        zweite instanceof SettlementNotFoundException
        settlements == 0
        carts == 0
    }

    def "Mitgliedschaftsänderung während einer Settlement-Änderung (#zuerst zuerst): kein Rückschreiben veralteter Werte (A11)"() {
        given: "Owner zahlte 10.00 an Ben; parallel: Owner ändert auf 20.00, Owner entfernt Ben aus der Gruppe"
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        loginAs(wg.owner)
        def paar = settlementService.addSettlementPayment(
                new SettlementPaymentDto(10d, wg.group.id, new UserDto(id: ben.id), DateUtils.toDate(TestData.REFERENCE_DATE)))
        def settlementId = paar[0].settlementId
        SecurityContextHolder.clearContext()
        def aendern = { settlementService.updateSettlementPayment(aenderung(settlementId, 20d, ben.id)) }
        def entfernen = {
            groupService.removeMemberFromGroup(new RemoveGroupMemberDto(id: wg.group.id, member: new UserDto(id: ben.id)))
        }
        LOG.info('Szenario: Gruppe {}, Settlement {} (Owner {} -> Ben {}, 10.00), Carts {}; T1 = {} hält offen, T2 wartet',
                wg.group.id, settlementId, wg.owner.id, ben.id, paar*.id, zuerst)

        when:
        zuerst == 'Settlement-Änderung'
                ? overlapping(wg.owner, wg.owner, aendern, entfernen)
                : overlapping(wg.owner, wg.owner, entfernen, aendern)
        def gespeichert = settlementZeile(settlementId)
        def carts = cartZeilen(settlementId)
        LOG.info('Nach beiden Commits: Settlement {}, Carts {}', gespeichert, carts)

        then: "Settlement und beide Einkäufe stimmen überein"
        gespeichert.amount == 20d
        gespeichert.receiver_id == ben.id
        carts*.amount == [20d, -20d]
        carts*.user_id == [wg.owner.id, ben.id]

        and: "Löschkennzeichen des entfernten Empfängers und Legacy-Anteil (2 Mitglieder am Kaufdatum) korrekt"
        carts*.is_deleted == [false, true]
        carts*.average_per_member == [10d, -10d]

        where:
        zuerst << ['Settlement-Änderung', 'Mitgliedschaftsänderung']
    }

    private static SettlementPaymentDto aenderung(Long settlementId, Double betrag, Long empfaengerId,
                                                  java.time.LocalDate datum = TestData.REFERENCE_DATE) {
        def dto = new SettlementPaymentDto(betrag, null, new UserDto(id: empfaengerId), DateUtils.toDate(datum))
        dto.id = settlementId
        dto
    }

    private Map<String, Object> settlementZeile(Long settlementId) {
        jdbcTemplate.queryForMap('select amount, date_purchased, payer_id, receiver_id from settlement where id = ?', settlementId)
    }

    private List<Map<String, Object>> cartZeilen(Long settlementId) {
        jdbcTemplate.queryForList(
                'select id, amount, date_purchased, user_id, is_deleted, average_per_member from cart where settlement_id = ? order by id',
                settlementId)
    }

    /**
     * Führt {@code erster} in Transaktion 1 (als {@code nutzer1}) aus und hält sie offen, startet {@code zweiter} in
     * Transaktion 2 (als {@code nutzer2}, eigener Thread), wartet, bis Transaktion 2 an einer Zeilensperre hängt, und gibt
     * dann den Commit von 1 frei. Liefert die Ergebnisse beider Aufrufe; ein Fehler in einem Thread scheitert über Future.get.
     */
    private List overlapping(User nutzer1, User nutzer2, Closure erster, Closure zweiter) {
        def ersterAusgefuehrt = new CountDownLatch(1)
        def commitFreigabe = new CountDownLatch(1)
        def pool = Executors.newFixedThreadPool(2)
        try {
            def t1 = pool.submit({
                inTransaction(nutzer1) {
                    def result = erster.call()
                    ersterAusgefuehrt.countDown()
                    assert commitFreigabe.await(TIMEOUT, TimeUnit.SECONDS)
                    result
                }
            } as Callable)
            assert ersterAusgefuehrt.await(TIMEOUT, TimeUnit.SECONDS)
            def t2 = pool.submit({ inTransaction(nutzer2) { zweiter.call() } } as Callable)
            new PollingConditions(timeout: TIMEOUT).eventually {
                assert wartendAufSperre() == 1
            }
            LOG.info('Transaktion 2 wartet an der Sperre, Transaktion 1 committet')
            commitFreigabe.countDown()
            [t1.get(TIMEOUT, TimeUnit.SECONDS), t2.get(TIMEOUT, TimeUnit.SECONDS)]
        } finally {
            commitFreigabe.countDown()
            pool.shutdownNow()
        }
    }

    private Object inTransaction(User user, Closure aufruf) {
        loginAs(user)
        try {
            transaction.execute { aufruf.call() }
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    /** Führt {@code aufruf} aus und liefert eine geworfene Exception als Ergebnis (die Transaktion committet). */
    private static Object abgefangen(Closure aufruf) {
        try {
            aufruf.call()
        } catch (Exception e) {
            e
        }
    }

    /** Zahl der Datenbankverbindungen, die gerade auf eine Sperre warten. */
    private int wartendAufSperre() {
        jdbcTemplate.queryForObject(
                "select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'",
                Integer)
    }

    /**
     * Spec-lokaler Trigger auf cart: zählt die Updates von Settlement-Carts in der laufenden Transaktion
     * (transaktionslokale Einstellung) und wirft erst beim zweiten – das erste Update ist dann sicher geschrieben,
     * unabhängig davon, in welcher Reihenfolge Hibernate Settlement und Carts beim Flush schreibt.
     */
    private void installiereZaehlTrigger() {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION div74_test_fail() RETURNS trigger AS \$\$
                DECLARE
                    anzahl integer;
                BEGIN
                    IF NEW.settlement_id IS NULL THEN
                        RETURN NEW;
                    END IF;
                    anzahl := coalesce(nullif(current_setting('div74.test.cart_updates', true), ''), '0')::integer + 1;
                    PERFORM set_config('div74.test.cart_updates', anzahl::text, true);
                    IF anzahl >= 2 THEN
                        RAISE EXCEPTION '${TRIGGER_MELDUNG}';
                    END IF;
                    RETURN NEW;
                END
                \$\$ LANGUAGE plpgsql""".toString())
        jdbcTemplate.execute('CREATE TRIGGER div74_test_fail BEFORE UPDATE ON cart FOR EACH ROW EXECUTE FUNCTION div74_test_fail()')
    }

    /**
     * Spec-lokaler Trigger, der bei erfüllter Bedingung eine Exception wirft. Funktion und Trigger werden im
     * cleanup:-Block mit entferneTrigger() gelöscht.
     */
    private void installiereTrigger(String tabelle, String zeitpunkt, String bedingung) {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION div74_test_fail() RETURNS trigger AS \$\$
                BEGIN
                    IF ${bedingung} THEN
                        RAISE EXCEPTION '${TRIGGER_MELDUNG}';
                    END IF;
                    RETURN COALESCE(NEW, OLD);
                END
                \$\$ LANGUAGE plpgsql""".toString())
        jdbcTemplate.execute("CREATE TRIGGER div74_test_fail ${zeitpunkt} ON ${tabelle} FOR EACH ROW EXECUTE FUNCTION div74_test_fail()".toString())
    }

    private void entferneTrigger(String tabelle = 'cart') {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS div74_test_fail ON ${tabelle}".toString())
        jdbcTemplate.execute('DROP FUNCTION IF EXISTS div74_test_fail()')
    }

    private long zaehle(String sql, Object... args) {
        jdbcTemplate.queryForObject(sql, Long, args)
    }

    private static String ursache(Throwable e) {
        def cause = e
        while (cause.cause != null && cause.cause != cause) {
            cause = cause.cause
        }
        cause.message
    }
}
