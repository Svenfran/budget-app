package com.github.svenfran.budgetapp.budgetappbackend.db

import com.github.svenfran.budgetapp.budgetappbackend.entity.Category
import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.service.SpendingsOverviewService
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.DatabaseCleaner
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.Fixtures.GroupScenario
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

import java.time.LocalDate

/**
 * Daten-SQL der DIV-74-Migration (Changeset 20261116120000-2): kennzeichnet Settlement-Kategorien und verknüpft
 * bestehende Ausgleichszahlungs-Paare. Die Testdatenbank hat das Changeset beim Start auf leeren Tabellen ausgeführt;
 * hier wird der Zustand davor mit Fixtures nachgebaut (Kategorie mit Settlement-Namen ohne Kennzeichen, Carts ohne
 * settlement_id) und dieselbe SQL-Datei erneut ausgeführt.
 *
 * - Commit-abhängig: Die Prüfungen im SQL zählen global, daher leere Datenbank vor jedem Feature (truncateAll).
 * - Die Datei ist ein einziger DO-Block und wird als Ganzes ausgeführt (keine Semikolon-Aufteilung).
 * - Nach einem erwarteten Abbruch wird mit neuen Abfragen geprüft, dass nichts geschrieben wurde.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SettlementLinkMigrationIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(SettlementLinkMigrationIntegrationSpec)

    private static final String MIGRATION_SQL = '/db/changelog/release-1.x/sql/20261116120000-2_linkExistingSettlementPayments.sql'

    private static final LocalDate TAG_1 = TestData.REFERENCE_DATE
    private static final LocalDate TAG_2 = TestData.REFERENCE_DATE.minusMonths(2)

    @Autowired JdbcTemplate jdbcTemplate
    @Autowired DatabaseCleaner databaseCleaner
    @Autowired SpendingsOverviewService spendingsOverviewService
    @Autowired PlatformTransactionManager transactionManager

    def setup() {
        databaseCleaner.truncateAll()
    }

    def "Saubere Paare werden verknüpft: Zahler ist der positive Cart, auch wenn der negative die kleinere Id hat"() {
        given: "Gruppe mit Anna (Owner) und Ben; zwei Paare in 'Ausgleichszahlung', ein normaler Einkauf; zweite Gruppe mit ' Repayment '"
        def anna = fixtures.user()
        def ben = fixtures.user()
        def wg = fixtures.group(owner: anna, members: [ben])
        def kategorie = fixtures.category(wg, name: 'Ausgleichszahlung')
        def benNeg = cart(wg, kategorie, ben, -30d, TAG_1)
        def annaPos = cart(wg, kategorie, anna, 30d, TAG_1)
        def benPos = cart(wg, kategorie, ben, 12.5d, TAG_2)
        def annaNeg = cart(wg, kategorie, anna, -12.5d, TAG_2)
        def normal = fixtures.cart(wg, user: anna, amount: 40d, datePurchased: DateUtils.toDate(TAG_1))
        def andereWg = fixtures.group(owner: ben, members: [anna])
        def andereKategorie = fixtures.category(andereWg, name: ' Repayment ')
        def anderePos = cart(andereWg, andereKategorie, ben, 7d, TAG_1)
        def andereNeg = cart(andereWg, andereKategorie, anna, -7d, TAG_1)
        def (groupId, andereGroupId, annaId, benId) = [wg.group.id, andereWg.group.id, anna.id, ben.id]
        def vorherFelder = saldoFelder()
        def vorherSalden = salden(wg, anna)
        LOG.info('Szenario: Gruppe {} (Anna {}, Ben {}), Kategorie {} "Ausgleichszahlung": Paar 1 Ben {} -30 / Anna {} +30 am {}, ' +
                'Paar 2 Ben {} +12.5 / Anna {} -12.5 am {}, normaler Einkauf {} in Kategorie {}; Gruppe {} Kategorie {} " Repayment ": ' +
                'Ben {} +7 / Anna {} -7', groupId, annaId, benId, kategorie.id, benNeg.id, annaPos.id, TAG_1, benPos.id, annaNeg.id,
                TAG_2, normal.id, wg.category.id, andereGroupId, andereKategorie.id, anderePos.id, andereNeg.id)

        when:
        migriere()
        def settlements = settlementsDerGruppe(groupId)
        def links = settlementIds(groupId) + settlementIds(andereGroupId)
        def gekennzeichnet = gekennzeichneteKategorien()
        def nachherFelder = saldoFelder()
        def nachherSalden = salden(wg, anna)
        LOG.info('Migration -> Settlements Gruppe {}: {}; Verknüpfungen {}; gekennzeichnet {}; Salden vorher {}, nachher {}',
                groupId, settlements, links, gekennzeichnet, vorherSalden, nachherSalden)

        then: "zwei Settlements, Zahler = User des positiven Carts, Betrag positiv, Ersteller unbekannt"
        settlements.collect { [it.payer_id, it.receiver_id, it.amount, it.date_purchased.toString(), it.created_by] } as Set == [
                [annaId, benId, 30d, TAG_1.toString(), null],
                [benId, annaId, 12.5d, TAG_2.toString(), null]] as Set

        and: "je Paar beide Carts mit derselben Settlement-Id, normaler Einkauf unverknüpft"
        links[annaPos.id] != null
        links[annaPos.id] == links[benNeg.id]
        links[benPos.id] != null
        links[benPos.id] == links[annaNeg.id]
        links[annaPos.id] != links[benPos.id]
        links[normal.id] == null
        links[anderePos.id] != null
        links[anderePos.id] == links[andereNeg.id]

        and: "Kategorien mit Settlement-Namen (auch mit Leerzeichen/Groß-Klein) gekennzeichnet, die Standardkategorie nicht"
        gekennzeichnet == [kategorie.id, andereKategorie.id] as Set

        and: "Beträge, Daten, User, Gruppen und Kategorien unverändert, Salden identisch (AC9)"
        nachherFelder == vorherFelder
        nachherSalden == vorherSalden
    }

    def "Gegenläufige Paare desselben Schlüssels werden über verschiedene User gepaart (Review-1-Gegenbeispiel)"() {
        given: "A+X (1), A-X (2), B+X (3), B-X (4) am selben Tag mit gleichem Betrag; eine von den Usern unabhängige Nummerierung würde (1,2)/(3,4) bilden"
        def anna = fixtures.user()
        def ben = fixtures.user()
        def wg = fixtures.group(owner: anna, members: [ben])
        def kategorie = fixtures.category(wg, name: 'Ausgleichszahlung')
        def annaPos = cart(wg, kategorie, anna, 20d, TAG_1)
        def annaNeg = cart(wg, kategorie, anna, -20d, TAG_1)
        def benPos = cart(wg, kategorie, ben, 20d, TAG_1)
        def benNeg = cart(wg, kategorie, ben, -20d, TAG_1)
        def groupId = wg.group.id
        LOG.info('Szenario: Gruppe {}, Anna {}, Ben {}: Carts Anna+ {}, Anna- {}, Ben+ {}, Ben- {} (je 20 am {})', groupId,
                anna.id, ben.id, annaPos.id, annaNeg.id, benPos.id, benNeg.id, TAG_1)

        when:
        migriere()
        def links = settlementIds(groupId)
        LOG.info('Migration -> Verknüpfungen {}', links)

        then:
        links.values().every { it != null }
        links[annaPos.id] == links[benNeg.id]
        links[benPos.id] == links[annaNeg.id]
        links[annaPos.id] != links[benPos.id]
    }

    def "Mehrere Paare gleicher Richtung am selben Tag mit gleichem Betrag werden deterministisch nach Id gepaart"() {
        given:
        def anna = fixtures.user()
        def ben = fixtures.user()
        def wg = fixtures.group(owner: anna, members: [ben])
        def kategorie = fixtures.category(wg, name: 'Ausgleichszahlung')
        def pos1 = cart(wg, kategorie, anna, 15d, TAG_1)
        def pos2 = cart(wg, kategorie, anna, 15d, TAG_1)
        def neg1 = cart(wg, kategorie, ben, -15d, TAG_1)
        def neg2 = cart(wg, kategorie, ben, -15d, TAG_1)
        def groupId = wg.group.id
        LOG.info('Szenario: Gruppe {}: Anna {} +15 (Carts {}, {}), Ben {} -15 (Carts {}, {}) am {}', groupId, anna.id, pos1.id,
                pos2.id, ben.id, neg1.id, neg2.id, TAG_1)

        when:
        migriere()
        def links = settlementIds(groupId)
        LOG.info('Migration -> Verknüpfungen {}', links)

        then:
        links[pos1.id] != null
        links[pos1.id] == links[neg1.id]
        links[pos2.id] == links[neg2.id]
        links[pos1.id] != links[pos2.id]
    }

    def "#fall: Migration bricht ab und schreibt nichts"() {
        given: "Anna (Owner), Ben, Cleo; Carts in 'Ausgleichszahlung' laut Tabelle (User-Index, Betrag)"
        def nutzer = [fixtures.user(), fixtures.user(), fixtures.user()]
        def wg = fixtures.group(owner: nutzer[0], members: nutzer[1..2])
        def kategorie = fixtures.category(wg, name: 'Ausgleichszahlung')
        def ids = carts.collect { cart(wg, kategorie, nutzer[it[0]], it[1] as Double, TAG_1).id }
        def (groupId, kategorieId) = [wg.group.id, kategorie.id]
        LOG.info('Szenario: Gruppe {} mit User {}; Kategorie {}; Carts {} = {}', groupId, nutzer*.id, kategorieId, ids, carts)

        when:
        LOG.info('Migration, erwartet Abbruch mit "{}"', meldung)
        migriere()

        then:
        def e = thrown(DataAccessException)
        e.mostSpecificCause.message.contains(meldung)

        and: "aus neuen Abfragen: keine Settlements, keine Verknüpfung, kein Kennzeichen"
        jdbcTemplate.queryForObject('select count(*) from settlement', Long) == 0
        settlementIds(groupId).values().every { it == null }
        gekennzeichneteKategorien().isEmpty()

        where:
        fall                          | carts                                         || meldung
        'Waise'                       | [[0, 30d]]                                    || '1 Ausgleichszahlungs-Carts ohne eindeutigen Partner'
        'Paar mit gleichem User'      | [[0, 30d], [0, -30d]]                         || '2 Ausgleichszahlungs-Carts ohne eindeutigen Partner'
        'Betrag 0'                    | [[0, 0d], [1, 0d]]                            || '2 Ausgleichszahlungs-Carts ohne eindeutigen Partner'
        'Paar plus Waise'             | [[0, 30d], [1, -30d], [1, 10d]]               || '1 Ausgleichszahlungs-Carts ohne eindeutigen Partner'
        'drei Beteiligte im Schlüssel' | [[0, 30d], [1, -30d], [2, 30d], [0, -30d]]   || '1 Schlüssel mit mehr als zwei Beteiligten'
    }

    private def cart(GroupScenario wg, Category kategorie, User user, Double betrag, LocalDate tag) {
        fixtures.cart(wg, user: user, category: kategorie, title: 'Ausgleichszahlung', description: '', amount: betrag,
                averagePerMember: betrag / 2, datePurchased: DateUtils.toDate(tag))
    }

    private void migriere() {
        jdbcTemplate.execute(getClass().getResource(MIGRATION_SQL).getText('UTF-8'))
    }

    private List<Map<String, Object>> settlementsDerGruppe(Long groupId) {
        jdbcTemplate.queryForList(
                'select payer_id, receiver_id, amount, date_purchased, created_by from settlement where group_id = ? order by id',
                groupId)
    }

    /** cart.id -> settlement_id der Gruppe. */
    private Map<Long, Long> settlementIds(Long groupId) {
        jdbcTemplate.queryForList('select id, settlement_id from cart where group_id = ? order by id', groupId)
                .collectEntries { [(it.id as Long): it.settlement_id as Long] }
    }

    private Set<Long> gekennzeichneteKategorien() {
        jdbcTemplate.queryForList('select id from category where is_settlement', Long) as Set
    }

    /** Saldorelevante Felder aller Carts (FA6: werden von der Migration nicht verändert). */
    private List<List<Object>> saldoFelder() {
        jdbcTemplate.queryForList('select id, amount, date_purchased, user_id, group_id, category_id from cart order by id')
                .collect { [it.id, it.amount, it.date_purchased, it.user_id, it.group_id, it.category_id] }
    }

    /** Salden der Gruppe (alle Jahre) aus Sicht des angemeldeten Owners, in einer eigenen Lese-Transaktion. */
    private Map<Long, Double> salden(GroupScenario wg, User betrachter) {
        loginAs(betrachter)
        new TransactionTemplate(transactionManager).execute {
            spendingsOverviewService.getSpendingsForGroupAndAllYears(wg.group.id)
                    .spendingsTotalYear.spendingsTotalUser.collectEntries { [it.userId, it.diff] }
        } as Map<Long, Double>
    }
}
