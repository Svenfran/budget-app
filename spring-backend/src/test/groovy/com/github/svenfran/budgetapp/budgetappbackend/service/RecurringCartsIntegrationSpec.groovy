package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartTemplateRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.DatabaseCleaner
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

import java.time.LocalDate

import static com.github.svenfran.budgetapp.budgetappbackend.constants.RecurrenceType.*

/**
 * Wiederkehrender Job (generateRecurringCarts) als Commit-Spec: Der Job läuft in seiner eigenen Transaktion und
 * verarbeitet alle aktiven Templates, daher kein Rollback, sondern Aufräumen vor jedem Feature.
 * Kein flushAndClear(): ohne Test-Transaktion committet jeder Repository-Aufruf selbst.
 * Datum über testClock (Standard TestData.REFERENCE_DATE).
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RecurringCartsIntegrationSpec extends IntegrationSpec {

    @Autowired CartService cartService
    @Autowired CartRepository cartRepository
    @Autowired CartTemplateRepository templateRepository
    @Autowired DatabaseCleaner databaseCleaner

    def setup() {
        databaseCleaner.truncateAll()
    }

    def "Scheduler erzeugt keinen Einkauf, wenn das Template noch nicht fällig ist"() {
        given:
        def tomorrow = testClock.today.plusDays(1)
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Gym', amount: 30d, datePurchased: DateUtils.toDate(tomorrow))
        def template = fixtures.template(cart, nextExecutionDate: tomorrow)

        when:
        cartService.generateRecurringCarts()

        then: "kein Einkauf erzeugt"
        cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id).size() == 1

        and: "Template unverändert und aktiv"
        def reloaded = templateRepository.findById(template.id).get()
        reloaded.nextExecutionDate == tomorrow
        reloaded.active
    }

    def "Scheduler erzeugt kein Duplikat, wenn ein gleicher Einkauf schon existiert"() {
        given:
        def today = testClock.today
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Netflix', amount: 12.99d, datePurchased: DateUtils.toDate(today))
        def template = fixtures.template(cart, nextExecutionDate: today)

        when:
        cartService.generateRecurringCarts()

        then: "kein weiterer Einkauf"
        cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id).size() == 1

        and: "Template auf den nächsten Termin verschoben"
        templateRepository.findById(template.id).get().nextExecutionDate == today.plusMonths(1)
    }

    def "Scheduler erzeugt einen neuen Einkauf aus dem fälligen Template"() {
        given:
        def today = testClock.today
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Spotify', amount: 9.99d, datePurchased: DateUtils.toDate(today.minusDays(1)))
        def template = fixtures.template(cart, nextExecutionDate: today)

        when:
        cartService.generateRecurringCarts()

        then: "ein neuer Einkauf mit den Werten des Templates"
        def carts = cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id)
        carts.size() == 2
        def created = carts.find { it.id != cart.id }
        created.title == 'Spotify'
        created.amount == 9.99d
        created.category.id == wg.category.id
        created.user.id == wg.owner.id
        created.group.id == wg.group.id
        DateUtils.toLocalDate(created.datePurchased) == today
        created.template.id == template.id

        and: "Template auf den nächsten Termin verschoben"
        templateRepository.findById(template.id).get().nextExecutionDate == today.plusMonths(1)
    }

    def "Scheduler überspringt ein aktives Template ohne Wiederholung und verarbeitet die übrigen"() {
        given:
        def today = testClock.today
        def wg = fixtures.group()
        def broken = fixtures.cart(wg, title: 'Einmalig', amount: 5d, datePurchased: DateUtils.toDate(today.minusDays(1)))
        def brokenTemplate = fixtures.template(broken, recurrenceType: NONE, nextExecutionDate: today)
        def valid = fixtures.cart(wg, title: 'Spotify', amount: 9.99d, datePurchased: DateUtils.toDate(today.minusDays(1)))
        def validTemplate = fixtures.template(valid, nextExecutionDate: today)

        when:
        cartService.generateRecurringCarts()

        then: "nur aus dem gültigen Template ein neuer Einkauf"
        def created = cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id)
                .findAll { !(it.id in [broken.id, valid.id]) }
        created*.title == ['Spotify']
        created.first().template.id == validTemplate.id

        and: "gültiges Template verschoben, Template ohne Wiederholung unverändert"
        templateRepository.findById(validTemplate.id).get().nextExecutionDate == today.plusMonths(1)
        def skipped = templateRepository.findById(brokenTemplate.id).get()
        skipped.nextExecutionDate == today
        skipped.active
    }

    def "Scheduler erzeugt bei #recurrenceType einen Einkauf und setzt den nächsten Termin auf #expectedNextDate"() {
        given:
        def today = testClock.today
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Gym', amount: 29.99d, datePurchased: DateUtils.toDate(today.minusDays(1)))
        def template = fixtures.template(cart, recurrenceType: recurrenceType, nextExecutionDate: today)

        when:
        cartService.generateRecurringCarts()

        then: "Original und neuer Einkauf"
        cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id).size() == 2

        and:
        templateRepository.findById(template.id).get().nextExecutionDate == expectedNextDate

        where:
        // testClock steht auf REFERENCE_DATE; Instanzfelder sind im where:-Block nicht verfügbar.
        recurrenceType || expectedNextDate
        DAILY          || TestData.REFERENCE_DATE.plusDays(1)
        WEEKLY         || TestData.REFERENCE_DATE.plusWeeks(1)
        MONTHLY        || TestData.REFERENCE_DATE.plusMonths(1)
        YEARLY         || TestData.REFERENCE_DATE.plusYears(1)
    }

    def "Fälliges #recurrenceType-Template am #heute erzeugt den Einkauf und setzt den nächsten Termin auf #erwartet"() {
        given:
        testClock.today = heute
        // Mitgliedschaft vor allen Terminen der Tabelle, sonst zählt der Job 0 Mitglieder zum Kaufdatum
        def wg = fixtures.group(memberSince: LocalDate.of(2023, 1, 1))
        def cart = fixtures.cart(wg, title: 'Miete', amount: 900d, datePurchased: DateUtils.toDate(heute.minusDays(1)))
        def template = fixtures.template(cart, recurrenceType: recurrenceType, nextExecutionDate: heute)

        when:
        cartService.generateRecurringCarts()

        then: "Einkauf am fälligen Tag"
        def created = cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id).findAll { it.id != cart.id }
        created.collect { DateUtils.toLocalDate(it.datePurchased) } == [heute]

        and: "nächster Termin auf das Monatsende gekappt"
        templateRepository.findById(template.id).get().nextExecutionDate == erwartet

        where:
        recurrenceType | heute                     || erwartet
        MONTHLY        | LocalDate.of(2025, 1, 31) || LocalDate.of(2025, 2, 28)
        MONTHLY        | LocalDate.of(2024, 1, 31) || LocalDate.of(2024, 2, 29)
        MONTHLY        | LocalDate.of(2025, 3, 31) || LocalDate.of(2025, 4, 30)
        YEARLY         | LocalDate.of(2024, 2, 29) || LocalDate.of(2025, 2, 28)
    }

    def "Monatliches Template vom 31. bleibt nach Februar auf dem 28. (bestehendes Verhalten)"() {
        // Dokumentiert das Ist-Verhalten (Drift): Der Folgetermin rechnet vom gekappten Datum weiter.
        // Siehe Plan DIV-9 Abschnitt 11 bzw. Bug DIV-25; mit dem Fix ist dieser Test anzupassen.
        given:
        testClock.today = LocalDate.of(2025, 1, 31)
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Miete', amount: 900d, datePurchased: DateUtils.toDate(LocalDate.of(2024, 12, 31)))
        def template = fixtures.template(cart, recurrenceType: MONTHLY, nextExecutionDate: LocalDate.of(2025, 1, 31))

        when: "Job am 31.01."
        cartService.generateRecurringCarts()

        then:
        templateRepository.findById(template.id).get().nextExecutionDate == LocalDate.of(2025, 2, 28)

        when: "Job am 28.02."
        testClock.today = LocalDate.of(2025, 2, 28)
        cartService.generateRecurringCarts()

        then: "Folgetermin 28.03. statt 31.03."
        templateRepository.findById(template.id).get().nextExecutionDate == LocalDate.of(2025, 3, 28)

        and: "Einkäufe am 31.01. und 28.02."
        def created = cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id).findAll { it.id != cart.id }
        created.collect { DateUtils.toLocalDate(it.datePurchased) } == [LocalDate.of(2025, 2, 28), LocalDate.of(2025, 1, 31)]
    }
}
