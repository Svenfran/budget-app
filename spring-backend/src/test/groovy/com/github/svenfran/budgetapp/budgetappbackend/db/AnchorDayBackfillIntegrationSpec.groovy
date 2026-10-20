package com.github.svenfran.budgetapp.budgetappbackend.db

import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartTemplateRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.springframework.beans.factory.annotation.Autowired

import java.time.LocalDate

import static com.github.svenfran.budgetapp.budgetappbackend.constants.RecurrenceType.*

/**
 * Daten-SQL der DIV-25-Migration (Ankertag befüllen, gedriftete Termine korrigieren). Die Testdatenbank hat das
 * Changeset beim Start auf leeren Tabellen ausgeführt; hier wird der Zustand davor nachgebaut (Ankertag mit falschem
 * Platzhalter, gedrifteter nächster Termin) und dieselbe SQL-Datei erneut ausgeführt. Die Anweisungen ändern alle
 * Zeilen, der Rollback der Test-Transaktion hält das folgenlos; geprüft wird nur das eigene Template.
 */
class AnchorDayBackfillIntegrationSpec extends IntegrationSpec {

    private static final String BACKFILL_SQL = '/db/changelog/release-1.x/sql/20261020120000-1_backfillAnchorDay.sql'

    @Autowired CartTemplateRepository templateRepository

    def "#fall: Ankertag #erwarteterAnker, nächster Termin #nextNachher"() {
        given:
        def wg = fixtures.group()
        def template = templateRepository.save(TestData.cartTemplate(
                userId: wg.owner.id, groupId: wg.group.id, categoryId: wg.category.id,
                recurrenceType: typ, active: aktiv, nextExecutionDate: nextVorher, anchorDay: 1))
        kaufdaten.each { fixtures.cart(wg, template: template, datePurchased: DateUtils.toDate(it)) }
        def templateId = template.id
        flushAndClear()

        when:
        runBackfill()
        flushAndClear()

        then:
        def migrated = templateRepository.findById(templateId).get()
        migrated.anchorDay == erwarteterAnker
        migrated.nextExecutionDate == nextNachher

        where:
        fall                           | typ     | aktiv | kaufdaten                                              | nextVorher                || erwarteterAnker | nextNachher
        'gedriftet, Anker 31'          | MONTHLY | true  | [LocalDate.of(2025, 1, 31), LocalDate.of(2025, 2, 28)]  | LocalDate.of(2025, 3, 28) || 31              | LocalDate.of(2025, 3, 31)
        'gedriftet, Anker 30'          | MONTHLY | true  | [LocalDate.of(2025, 1, 30), LocalDate.of(2025, 2, 28)]  | LocalDate.of(2025, 3, 28) || 30              | LocalDate.of(2025, 3, 30)
        'im Februar korrekt gekappt'   | MONTHLY | true  | [LocalDate.of(2024, 12, 31), LocalDate.of(2025, 1, 31)] | LocalDate.of(2025, 2, 28) || 31              | LocalDate.of(2025, 2, 28)
        // Grenze der Heuristik: der volle Tag ist mit dem Ausgangseinkauf gelöscht und nicht rekonstruierbar
        'Ausgangseinkauf gelöscht'     | MONTHLY | true  | [LocalDate.of(2025, 2, 28), LocalDate.of(2025, 3, 28)]  | LocalDate.of(2025, 4, 28) || 28              | LocalDate.of(2025, 4, 28)
        'nicht gedriftet'              | MONTHLY | true  | [LocalDate.of(2025, 5, 15)]                             | LocalDate.of(2025, 6, 15) || 15              | LocalDate.of(2025, 6, 15)
        'jährlich vom 29.02.'          | YEARLY  | true  | [LocalDate.of(2024, 2, 29)]                             | LocalDate.of(2025, 2, 28) || 29              | LocalDate.of(2025, 2, 28)
        'inaktiv bleibt unkorrigiert'  | MONTHLY | false | [LocalDate.of(2025, 1, 31), LocalDate.of(2025, 2, 28)]  | LocalDate.of(2025, 3, 28) || 31              | LocalDate.of(2025, 3, 28)
        'wöchentlich'                  | WEEKLY  | true  | [LocalDate.of(2025, 1, 31)]                             | LocalDate.of(2025, 2, 7)  || 7               | LocalDate.of(2025, 2, 7)
        'ohne Einkäufe'                | MONTHLY | true  | []                                                      | LocalDate.of(2025, 3, 28) || 28              | LocalDate.of(2025, 3, 28)
    }

    private void runBackfill() {
        def sql = getClass().getResource(BACKFILL_SQL).getText('UTF-8')
        sql.split(';')*.trim().findAll { it }.each {
            entityManager.createNativeQuery(it).executeUpdate()
        }
    }
}
