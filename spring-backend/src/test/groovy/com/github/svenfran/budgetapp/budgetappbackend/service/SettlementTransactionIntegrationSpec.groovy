package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.SettlementPaymentDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.DatabaseCleaner
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Transaktionsgrenzen der Ausgleichszahlungen (DIV-74, AC6): Ein Fehler nach einem bereits ausgeführten Write darf keinen
 * Teilzustand hinterlassen.
 *
 * - Commit-abhängig: keine Test-Transaktion, damit eine fehlende Service-Transaktion nicht von der Test-Transaktion
 *   verdeckt wird; Aufräumen vor jedem Feature.
 * - Der Fehler entsteht über einen spec-lokalen Postgres-Trigger erst nach einem tatsächlich ausgeführten SQL-Write. Der
 *   Trigger wird im cleanup:-Block in jedem Fall wieder entfernt.
 * - Geprüft wird nach dem Fehler mit neuen Abfragen über JdbcTemplate (eigene, frische Verbindung/Transaktion).
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SettlementTransactionIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(SettlementTransactionIntegrationSpec)

    private static final String TRIGGER_MELDUNG = 'div74-test: Write abgelehnt'

    @Autowired SettlementService settlementService
    @Autowired CartService cartService
    @Autowired JdbcTemplate jdbcTemplate
    @Autowired DatabaseCleaner databaseCleaner

    def setup() {
        databaseCleaner.truncateAll()
        LocaleContextHolder.setLocale(Locale.GERMAN)
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
