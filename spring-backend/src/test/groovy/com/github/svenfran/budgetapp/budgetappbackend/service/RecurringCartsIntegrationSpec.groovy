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
        def (categoryId, ownerId, groupId, templateId) = [wg.category.id, wg.owner.id, wg.group.id, template.id]
        created.category.id == categoryId
        created.user.id == ownerId
        created.group.id == groupId
        DateUtils.toLocalDate(created.datePurchased) == today
        created.template.id == templateId

        and: "Template auf den nächsten Termin verschoben"
        def nextExecutionDate = templateRepository.findById(templateId).get().nextExecutionDate
        nextExecutionDate == today.plusMonths(1)
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

    def "Scheduler deaktiviert ein fälliges Template ohne Mitglieder zum Datum und verarbeitet die übrigen"() {
        given:
        def today = testClock.today
        def leer = fixtures.group(memberSince: today.plusDays(1))
        def verwaist = fixtures.cart(leer, title: 'Strom', amount: 60d, datePurchased: DateUtils.toDate(today.minusDays(1)))
        def verwaistTemplate = fixtures.template(verwaist, nextExecutionDate: today)
        def wg = fixtures.group()
        def valid = fixtures.cart(wg, title: 'Spotify', amount: 9.99d, datePurchased: DateUtils.toDate(today.minusDays(1)))
        def validTemplate = fixtures.template(valid, nextExecutionDate: today)

        when:
        cartService.generateRecurringCarts()

        then: "kein Einkauf in der Gruppe ohne Mitglieder, Template deaktiviert"
        cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(leer.group.id)*.id == [verwaist.id]
        def deaktiviert = templateRepository.findById(verwaistTemplate.id).get()
        !deaktiviert.active
        deaktiviert.endDate == today
        deaktiviert.nextExecutionDate == today

        and: "übriges Template normal verarbeitet"
        cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id).size() == 2
        templateRepository.findById(validTemplate.id).get().nextExecutionDate == today.plusMonths(1)
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
        // Mitgliedschaft vor allen Terminen der Tabelle, sonst zählt der Job 0 Mitglieder zum Kaufdatum und deaktiviert das Template
        def wg = fixtures.group(memberSince: LocalDate.of(2023, 1, 1))
        def cart = fixtures.cart(wg, title: 'Miete', amount: 900d, datePurchased: DateUtils.toDate(heute.minusDays(1)))
        def template = fixtures.template(cart, recurrenceType: recurrenceType, nextExecutionDate: heute, anchorDay: heute.dayOfMonth)

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

    // ----- Verpasste Termine (DIV-26) -----
    // Der Ausgangseinkauf liegt jeweils vor dem ersten verpassten Termin, damit die Duplikatprüfung nicht greift.

    def "Scheduler holt einen verpassten Termin nach und setzt den nächsten Termin hinter heute"() {
        given:
        def missed = testClock.today.minusDays(2)
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Spotify', amount: 9.99d, datePurchased: DateUtils.toDate(missed.minusMonths(1)))
        def template = fixtures.template(cart, recurrenceType: MONTHLY, nextExecutionDate: missed)

        when:
        cartService.generateRecurringCarts()

        then: "Einkauf am verpassten Termin"
        def created = cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id).findAll { it.id != cart.id }
        created.collect { DateUtils.toLocalDate(it.datePurchased) } == [missed]

        and: "nächster Termin einen Monat nach dem verpassten"
        templateRepository.findById(template.id).get().nextExecutionDate == missed.plusMonths(1)
    }

    def "Scheduler erzeugt bei #recurrenceType für jeden verpassten Termin einen Einkauf"() {
        given:
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Gym', amount: 29.99d, datePurchased: DateUtils.toDate(firstMissed.minusDays(1)))
        def template = fixtures.template(cart, recurrenceType: recurrenceType, nextExecutionDate: firstMissed)

        when:
        cartService.generateRecurringCarts()

        then: "ein Einkauf je verpasstem Termin bis einschließlich heute"
        def created = cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id).findAll { it.id != cart.id }
        created.collect { DateUtils.toLocalDate(it.datePurchased) } == expectedDates

        and:
        templateRepository.findById(template.id).get().nextExecutionDate == expectedNextDate

        where:
        // testClock steht auf REFERENCE_DATE; Instanzfelder sind im where:-Block nicht verfügbar.
        recurrenceType | firstMissed                            || expectedDates                                                                                                          || expectedNextDate
        DAILY          | TestData.REFERENCE_DATE.minusDays(3)   || [TestData.REFERENCE_DATE, TestData.REFERENCE_DATE.minusDays(1), TestData.REFERENCE_DATE.minusDays(2), TestData.REFERENCE_DATE.minusDays(3)] || TestData.REFERENCE_DATE.plusDays(1)
        WEEKLY         | TestData.REFERENCE_DATE.minusWeeks(2)  || [TestData.REFERENCE_DATE, TestData.REFERENCE_DATE.minusWeeks(1), TestData.REFERENCE_DATE.minusWeeks(2)]                                || TestData.REFERENCE_DATE.plusWeeks(1)
        MONTHLY        | TestData.REFERENCE_DATE.minusMonths(2) || [TestData.REFERENCE_DATE, TestData.REFERENCE_DATE.minusMonths(1), TestData.REFERENCE_DATE.minusMonths(2)]                              || TestData.REFERENCE_DATE.plusMonths(1)
        YEARLY         | TestData.REFERENCE_DATE.minusYears(1)  || [TestData.REFERENCE_DATE, TestData.REFERENCE_DATE.minusYears(1)]                                                                  || TestData.REFERENCE_DATE.plusYears(1)
    }

    def "Scheduler überspringt beim Nachholen nur den Termin, an dem es schon einen gleichen Einkauf gibt"() {
        given:
        def today = testClock.today
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Gym', amount: 30d, datePurchased: DateUtils.toDate(today.minusWeeks(2).minusDays(1)))
        def template = fixtures.template(cart, recurrenceType: WEEKLY, nextExecutionDate: today.minusWeeks(2))
        def duplicate = fixtures.cart(wg, title: 'Gym', amount: 30d, datePurchased: DateUtils.toDate(today.minusWeeks(1)))

        when:
        cartService.generateRecurringCarts()

        then: "nur an den Terminen ohne gleichen Einkauf ein neuer Einkauf"
        def created = cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id)
                .findAll { !(it.id in [cart.id, duplicate.id]) }
        created.collect { DateUtils.toLocalDate(it.datePurchased) } == [today, today.minusWeeks(2)]

        and:
        templateRepository.findById(template.id).get().nextExecutionDate == today.plusWeeks(1)
    }

    def "Scheduler erkennt einen gleichen Einkauf auch ohne Beschreibung (Template #templateDescription, Einkauf #existingDescription)"() {
        given:
        def today = testClock.today
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Gym', description: templateDescription, amount: 30d, datePurchased: DateUtils.toDate(today.minusDays(1)))
        def template = fixtures.template(cart, nextExecutionDate: today)
        def duplicate = fixtures.cart(wg, title: 'Gym', description: existingDescription, amount: 30d, datePurchased: DateUtils.toDate(today))

        when:
        cartService.generateRecurringCarts()

        then: "kein weiterer Einkauf"
        cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id)*.id == [duplicate.id, cart.id]

        and:
        templateRepository.findById(template.id).get().nextExecutionDate == today.plusMonths(1)

        where:
        templateDescription | existingDescription
        null                | null
        null                | ''
        ''                  | null
    }

    def "Nachgeholte Einkäufe teilen durch die Mitgliederzahl am jeweiligen Termin"() {
        given:
        def today = testClock.today
        def ben = fixtures.user()
        def wg = fixtures.group()
        fixtures.membership(wg, ben, membershipStart: DateUtils.toDate(today.minusWeeks(1)))
        def cart = fixtures.cart(wg, title: 'Gym', amount: 30d, datePurchased: DateUtils.toDate(today.minusWeeks(2).minusDays(1)))
        fixtures.template(cart, recurrenceType: WEEKLY, nextExecutionDate: today.minusWeeks(2))

        when:
        cartService.generateRecurringCarts()

        then: "vor Bens Beitritt ein Mitglied, danach zwei"
        def created = cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id).findAll { it.id != cart.id }
        created.collect { [DateUtils.toLocalDate(it.datePurchased), it.averagePerMember] } ==
                [[today, 15d], [today.minusWeeks(1), 15d], [today.minusWeeks(2), 30d]]
    }

    def "Scheduler deaktiviert ein Template, wenn am verpassten Termin keine Mitglieder in der Gruppe sind"() {
        given:
        def today = testClock.today
        def leer = fixtures.group(memberSince: today.minusDays(1))
        def cart = fixtures.cart(leer, title: 'Strom', amount: 60d, datePurchased: DateUtils.toDate(today.minusDays(3)))
        def template = fixtures.template(cart, recurrenceType: DAILY, nextExecutionDate: today.minusDays(2))

        when:
        cartService.generateRecurringCarts()

        then: "kein Einkauf"
        cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(leer.group.id)*.id == [cart.id]

        and: "Template deaktiviert, nächster Termin bleibt auf dem Termin ohne Mitglieder"
        def deaktiviert = templateRepository.findById(template.id).get()
        !deaktiviert.active
        deaktiviert.endDate == today
        deaktiviert.nextExecutionDate == today.minusDays(2)
    }

    def "Scheduler holt Termine nach und deaktiviert das Template am ersten Termin ohne Mitglieder"() {
        given:
        def today = testClock.today
        // Mitgliedschaften enden vor dem zweiten verpassten Termin (today-1w)
        def wg = fixtures.group(memberUntil: today.minusDays(8))
        def cart = fixtures.cart(wg, title: 'Strom', amount: 60d, datePurchased: DateUtils.toDate(today.minusWeeks(2).minusDays(1)))
        def template = fixtures.template(cart, recurrenceType: WEEKLY, nextExecutionDate: today.minusWeeks(2))

        when:
        cartService.generateRecurringCarts()

        then: "nur der Termin mit Mitgliedern wird nachgeholt"
        def created = cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id).findAll { it.id != cart.id }
        created.collect { DateUtils.toLocalDate(it.datePurchased) } == [today.minusWeeks(2)]

        and: "Template deaktiviert, nächster Termin auf dem Abbruchtermin"
        def deaktiviert = templateRepository.findById(template.id).get()
        !deaktiviert.active
        deaktiviert.endDate == today
        deaktiviert.nextExecutionDate == today.minusWeeks(1)
    }

    def "Ein zweiter Joblauf am selben Tag erzeugt nach dem Nachholen nichts Neues"() {
        given:
        def today = testClock.today
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Gym', amount: 30d, datePurchased: DateUtils.toDate(today.minusWeeks(2).minusDays(1)))
        def template = fixtures.template(cart, recurrenceType: WEEKLY, nextExecutionDate: today.minusWeeks(2))
        cartService.generateRecurringCarts()
        def idsAfterFirstRun = cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id)*.id

        when:
        cartService.generateRecurringCarts()

        then: "Ausgangseinkauf und drei nachgeholte Einkäufe, keine weiteren"
        idsAfterFirstRun.size() == 4
        cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id)*.id == idsAfterFirstRun

        and: "Template unverändert"
        def reloaded = templateRepository.findById(template.id).get()
        reloaded.nextExecutionDate == today.plusWeeks(1)
        reloaded.active
    }

    // ----- Ankertag (DIV-25) -----

    def "Monatliches Template vom 31. landet nach Februar wieder auf dem 31."() {
        given:
        testClock.today = LocalDate.of(2025, 1, 31)
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Miete', amount: 900d, datePurchased: DateUtils.toDate(LocalDate.of(2024, 12, 31)))
        def template = fixtures.template(cart, recurrenceType: MONTHLY, nextExecutionDate: LocalDate.of(2025, 1, 31), anchorDay: 31)

        when: "Job am 31.01."
        cartService.generateRecurringCarts()

        then:
        templateRepository.findById(template.id).get().nextExecutionDate == LocalDate.of(2025, 2, 28)

        when: "Job am 28.02."
        testClock.today = LocalDate.of(2025, 2, 28)
        cartService.generateRecurringCarts()

        then: "Folgetermin wieder auf dem Ankertag"
        templateRepository.findById(template.id).get().nextExecutionDate == LocalDate.of(2025, 3, 31)

        and: "Einkäufe am 31.01. und 28.02."
        def created = cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id).findAll { it.id != cart.id }
        created.collect { DateUtils.toLocalDate(it.datePurchased) } == [LocalDate.of(2025, 2, 28), LocalDate.of(2025, 1, 31)]
    }

    def "Jährliches Template vom 29.02. trifft im nächsten Schaltjahr wieder den 29.02."() {
        given: "Job lief seit Februar 2025 nicht; Mitgliedschaft vor allen Terminen"
        testClock.today = LocalDate.of(2028, 2, 29)
        def wg = fixtures.group(memberSince: LocalDate.of(2023, 1, 1))
        def cart = fixtures.cart(wg, title: 'Versicherung', amount: 120d, datePurchased: DateUtils.toDate(LocalDate.of(2024, 2, 29)))
        def template = fixtures.template(cart, recurrenceType: YEARLY, nextExecutionDate: LocalDate.of(2025, 2, 28), anchorDay: 29)

        when:
        cartService.generateRecurringCarts()

        then: "in Nicht-Schaltjahren am 28.02., im Schaltjahr am 29.02."
        def created = cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id).findAll { it.id != cart.id }
        created.collect { DateUtils.toLocalDate(it.datePurchased) } ==
                [LocalDate.of(2028, 2, 29), LocalDate.of(2027, 2, 28), LocalDate.of(2026, 2, 28), LocalDate.of(2025, 2, 28)]

        and:
        templateRepository.findById(template.id).get().nextExecutionDate == LocalDate.of(2029, 2, 28)
    }
}
