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
        installiereTrigger('BEFORE INSERT', 'NEW.settlement_id IS NOT NULL AND NEW.amount < 0')
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

    /**
     * Spec-lokaler Trigger auf cart, der bei erfüllter Bedingung eine Exception wirft. Funktion und Trigger werden im
     * cleanup:-Block mit entferneTrigger() gelöscht.
     */
    private void installiereTrigger(String zeitpunkt, String bedingung) {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION div74_test_fail() RETURNS trigger AS \$\$
                BEGIN
                    IF ${bedingung} THEN
                        RAISE EXCEPTION '${TRIGGER_MELDUNG}';
                    END IF;
                    RETURN NEW;
                END
                \$\$ LANGUAGE plpgsql""".toString())
        jdbcTemplate.execute("CREATE TRIGGER div74_test_fail ${zeitpunkt} ON cart FOR EACH ROW EXECUTE FUNCTION div74_test_fail()".toString())
    }

    private void entferneTrigger() {
        jdbcTemplate.execute('DROP TRIGGER IF EXISTS div74_test_fail ON cart')
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
