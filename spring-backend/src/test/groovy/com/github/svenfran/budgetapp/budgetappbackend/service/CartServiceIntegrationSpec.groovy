package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.constants.RecurrenceType
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto
import com.github.svenfran.budgetapp.budgetappbackend.entity.CartTemplate
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.DatePurchasedNotWithinMembershipPeriodException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOfCartException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOrMemberOfGroupException
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartTemplateRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupMembershipHistoryRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.Fixtures.GroupScenario
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

import java.time.LocalDate

import static com.github.svenfran.budgetapp.budgetappbackend.constants.RecurrenceType.*

class CartServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(CartServiceIntegrationSpec)

    @Autowired CartService cartService
    @Autowired CartRepository cartRepository
    @Autowired CartTemplateRepository templateRepository
    @Autowired GroupMembershipHistoryRepository membershipRepository

    def "Bearbeiten #originalRecurrenceType -> #updatedRecurrenceType mit Titel '#newTitle' (Template-Update: #updateSelected) hinterlässt aktive Templates #expectedActiveTypes"() {
        given:
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Lunch')
        if (originalRecurrenceType != NONE) {
            fixtures.template(cart, recurrenceType: originalRecurrenceType)
        }

        and:
        def dto = new CartDto(cart)
        dto.title = newTitle
        dto.recurrenceType = updatedRecurrenceType
        dto.templateUpdateSelected = updateSelected
        LOG.info('Szenario: Gruppe {}, Owner {}, Einkauf {} Lunch, Template {}', wg.group.id, wg.owner.id, cart.id,
                originalRecurrenceType)

        when:
        def result = cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, wg.category, 1)
        flushAndClear()
        LOG.info('cartService.updateCartAndTemplateIfValid(Cart {}: title {}, recurrenceType {}, templateUpdate {}) -> ' +
                'title {}, recurrenceType {}', cart.id, newTitle, updatedRecurrenceType, updateSelected, result.title,
                result.recurrenceType)

        then:
        result.title == expectedTitle
        result.recurrenceType == (updatedRecurrenceType ?: NONE)
        def savedTitle = cartRepository.findById(cart.id).get().title
        savedTitle == expectedTitle

        and:
        activeTemplateTypes(wg) == expectedActiveTypes

        where:
        originalRecurrenceType | updatedRecurrenceType | newTitle        | updateSelected || expectedTitle   | expectedActiveTypes
        MONTHLY                | NONE                  | 'Lunch'         | false          || 'Lunch'         | []
        MONTHLY                | WEEKLY                | 'Lunch'         | false          || 'Lunch'         | [WEEKLY]
        NONE                   | NONE                  | 'Lunch Updated' | false          || 'Lunch Updated' | []
        MONTHLY                | MONTHLY               | 'Lunch'         | true           || 'Lunch'         | [MONTHLY]
        MONTHLY                | MONTHLY               | 'Lunch Updated' | true           || 'Lunch Updated' | [MONTHLY]
        DAILY                  | NONE                  | 'Lunch'         | true           || 'Lunch'         | []
        DAILY                  | NONE                  | 'Lunch Updated' | true           || 'Lunch Updated' | []
        MONTHLY                | null                  | 'Lunch'         | true           || 'Lunch'         | []
        NONE                   | null                  | 'Lunch Updated' | true           || 'Lunch Updated' | []
        NONE                   | null                  | 'Lunch'         | false          || 'Lunch'         | []
    }

    def "Änderung an #feld speichert den Einkauf mit Durchschnitt #expectedAverage"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def other = fixtures.category(wg)
        def cart = fixtures.cart(wg, amount: 40d, averagePerMember: 40d)

        and:
        def dto = new CartDto(cart)
        dto.recurrenceType = NONE
        dto.templateUpdateSelected = false
        change(dto, other)
        // wie CartService.updateCart: die Kategorie wird über die ID aus dem DTO bestimmt
        def category = [wg.category, other].find { it.id == dto.categoryDto.id }
        LOG.info('Szenario: Gruppe {}, Owner {}, Ben {}, Einkauf {} 40.00 (averagePerMember 40.00), Kategorien {} / {}; ' +
                'geändert: {}', wg.group.id, wg.owner.id, ben.id, cart.id, wg.category.id, other.id, feld)

        when:
        // groupMemberCount 2 = Owner + Ben (in CartService.updateCart aus der Mitgliedschaftshistorie ermittelt)
        cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, category, 2)
        flushAndClear()
        def saved = cartRepository.findById(cart.id).get()
        LOG.info('cartService.updateCartAndTemplateIfValid(Cart {}, 2 Mitglieder) -> averagePerMember {}', cart.id,
                saved.averagePerMember)

        then:
        saved.averagePerMember == expectedAverage

        and: "der gespeicherte Einkauf entspricht dem DTO"
        saved.title == dto.title
        saved.description == dto.description
        saved.amount == dto.amount
        DateUtils.toLocalDate(saved.datePurchased) == DateUtils.toLocalDate(dto.datePurchased)
        saved.category.id == dto.categoryDto.id

        where:
        // Closure-Parameter: Instanzfelder (fixtures) sind im where:-Block nicht verfügbar, Namen dürfen nicht mit
        // lokalen Variablen des Features kollidieren.
        feld            | change                                                                                         || expectedAverage
        'title'         | { CartDto c, otherCategory -> c.title = 'Neu' }                                                || 20d
        'description'   | { CartDto c, otherCategory -> c.description = 'Andere' }                                       || 20d
        'amount'        | { CartDto c, otherCategory -> c.amount = 60d }                                                 || 30d
        'datePurchased' | { CartDto c, otherCategory -> c.datePurchased = DateUtils.toDate(TestData.REFERENCE_DATE.plusDays(1)) } || 20d
        'category'      | { CartDto c, otherCategory -> c.categoryDto = TestData.categoryDto(otherCategory) }            || 20d
        'noChange'      | { CartDto c, otherCategory -> }                                                                || 40d
    }

    def "Wiederholung #oldType -> #newType: altes Template aktiv = #originalActive, aktive Templates #activeTypes"() {
        given:
        def wg = fixtures.group()
        def cart = fixtures.cart(wg)
        def original = fixtures.template(cart, recurrenceType: oldType)

        and: "nur die Wiederholung ändert sich, kein Template-Update"
        def dto = new CartDto(cart)
        dto.recurrenceType = newType
        dto.templateUpdateSelected = false
        LOG.info('Szenario: Gruppe {}, Einkauf {}, Template {} {}; neue Wiederholung {}', wg.group.id, cart.id, original.id,
                oldType, newType)

        when:
        cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, wg.category, 1)
        flushAndClear()
        def originalStillActive = templateRepository.findById(original.id).get().active
        LOG.info('cartService.updateCartAndTemplateIfValid(Cart {}: recurrenceType {}) -> Template {} aktiv={}', cart.id,
                newType, original.id, originalStillActive)

        then:
        originalStillActive == originalActive
        activeTemplateTypes(wg) == activeTypes

        where:
        oldType | newType || originalActive | activeTypes
        MONTHLY | MONTHLY || true           | [MONTHLY]
        MONTHLY | NONE    || false          | []
        MONTHLY | DAILY   || false          | [DAILY]
        DAILY   | WEEKLY  || false          | [WEEKLY]
        WEEKLY  | WEEKLY  || true           | [WEEKLY]
        NONE    | NONE    || true           | [NONE]
        MONTHLY | null    || false          | []
    }

    def "Template-Anlage mit #inputRecurrenceType ergibt aktive Templates #expectedTypes"() {
        given:
        def wg = fixtures.group()
        def cart = fixtures.cart(wg)
        LOG.info('Szenario: Gruppe {}, Owner {}, Einkauf {} ohne Template', wg.group.id, wg.owner.id, cart.id)

        when:
        cartService.createTemplateForCartIfRecurrenceTypeValid(cart, wg.owner, wg.group, inputRecurrenceType)
        flushAndClear()
        def templates = templateRepository.findByGroupId(wg.group.id)
        LOG.info('cartService.createTemplateForCartIfRecurrenceTypeValid(Cart {}, {}) -> Templates {} {}', cart.id,
                inputRecurrenceType, templates*.id, templates*.recurrenceType)

        then:
        templates*.recurrenceType == expectedTypes
        templates.every { it.active }
        def cartRecurrenceType = cartRepository.findById(cart.id).get().template?.recurrenceType
        cartRecurrenceType == expectedTypes[0]

        where:
        inputRecurrenceType || expectedTypes
        NONE                || []
        WEEKLY              || [WEEKLY]
        MONTHLY             || [MONTHLY]
        DAILY               || [DAILY]
        YEARLY              || [YEARLY]
    }

    def "Bearbeiten von Titel, Beschreibung und Betrag speichert die neuen Werte ohne Template"() {
        given:
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Groceries', amount: 50d)

        and:
        def dto = new CartDto(cart)
        dto.title = 'Weekly Groceries'
        dto.description = 'desc of Weekly Groceries'
        dto.amount = 75d
        LOG.info('Szenario: Gruppe {}, Einkauf {} Groceries 50.00 ohne Template', wg.group.id, cart.id)

        when:
        def result = cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, wg.category, 1)
        flushAndClear()
        LOG.info('cartService.updateCartAndTemplateIfValid(Cart {}: title, description, amount) -> title {}, amount {}, ' +
                'recurrenceType {}', cart.id, result.title, result.amount, result.recurrenceType)

        then:
        result.title == 'Weekly Groceries'
        result.amount == 75d
        result.description == 'desc of Weekly Groceries'
        result.recurrenceType == NONE

        and:
        def saved = cartRepository.findById(cart.id).get()
        saved.title == 'Weekly Groceries'
        saved.amount == 75d
        saved.description == 'desc of Weekly Groceries'

        and: "kein Template angelegt"
        templateRepository.findByGroupId(wg.group.id).isEmpty()
    }

    def "Bearbeiten ohne Wiederholung legt kein Template an"() {
        given:
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Books', amount: 30d)

        and:
        def dto = new CartDto(cart)
        dto.recurrenceType = NONE
        LOG.info('Szenario: Gruppe {}, Einkauf {} Books 30.00 ohne Template', wg.group.id, cart.id)

        when:
        def result = cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, wg.category, 1)
        flushAndClear()
        LOG.info('cartService.updateCartAndTemplateIfValid(Cart {}: recurrenceType NONE) -> title {}, recurrenceType {}',
                cart.id, result.title, result.recurrenceType)

        then:
        result.title == 'Books'
        result.recurrenceType == NONE
        templateRepository.findByGroupId(wg.group.id).isEmpty()
    }

    def "Wechsel der Wiederholung ersetzt das Template"() {
        given: "Kaufdatum kurz vor heute (testClock): calculateNextDate rechnet ab heute"
        def wg = fixtures.group()
        def purchased = TestData.REFERENCE_DATE.minusDays(3)
        def cart = fixtures.cart(wg, title: 'Electricity', amount: 80d, datePurchased: DateUtils.toDate(purchased))
        def original = fixtures.template(cart, recurrenceType: MONTHLY)

        and:
        def dto = new CartDto(cart)
        dto.recurrenceType = WEEKLY
        LOG.info('Szenario: heute {}, Gruppe {}, Owner {}, Einkauf {} Electricity 80.00 vom {}, Template {} MONTHLY',
                testClock.today, wg.group.id, wg.owner.id, cart.id, purchased, original.id)

        when:
        def result = cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, wg.category, 1)
        flushAndClear()
        LOG.info('cartService.updateCartAndTemplateIfValid(Cart {}: recurrenceType WEEKLY) -> recurrenceType {}', cart.id,
                result.recurrenceType)

        then:
        result.recurrenceType == WEEKLY
        def originalStillActive = templateRepository.findById(original.id).get().active
        !originalStillActive

        and: "genau ein aktives, neues Template"
        def active = templateRepository.findByGroupId(wg.group.id).findAll { it.active }
        active.size() == 1
        def replacement = active.first()
        replacement.recurrenceType == WEEKLY
        replacement.title == 'Electricity'
        replacement.amount == 80d
        replacement.nextExecutionDate == purchased.plusWeeks(1)
        def (ownerId, groupId, categoryId) = [wg.owner.id, wg.group.id, wg.category.id]
        replacement.userId == ownerId
        replacement.groupId == groupId
        replacement.categoryId == categoryId
        def cartTemplateId = cartRepository.findById(cart.id).get().template.id
        replacement.id == cartTemplateId
    }

    // ----- Ankertag (DIV-25, Entscheidung E3) -----

    def "Neues monatliches Template vom 31.01. merkt sich den 31. als Ankertag"() {
        given:
        testClock.today = LocalDate.of(2025, 1, 31)
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Miete', datePurchased: DateUtils.toDate(LocalDate.of(2025, 1, 31)))
        LOG.info('Szenario: heute {}, Gruppe {}, Einkauf {} Miete vom 2025-01-31 ohne Template', testClock.today,
                wg.group.id, cart.id)

        when:
        cartService.createTemplateForCartIfRecurrenceTypeValid(cart, wg.owner, wg.group, MONTHLY)
        flushAndClear()
        def template = templateRepository.findByGroupId(wg.group.id).first()
        LOG.info('cartService.createTemplateForCartIfRecurrenceTypeValid(Cart {}, MONTHLY) -> Template {}: anchorDay {}, ' +
                'nextExecutionDate {}', cart.id, template.id, template.anchorDay, template.nextExecutionDate)

        then:
        template.anchorDay == 31
        template.nextExecutionDate == LocalDate.of(2025, 2, 28)
    }

    def "Template-Update über einen gekappten Einkauf mit unverändertem Datum behält den Ankertag"() {
        given: "Einkauf vom 28.02. aus einem Template vom 31."
        testClock.today = LocalDate.of(2025, 2, 28)
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Miete', amount: 900d, datePurchased: DateUtils.toDate(LocalDate.of(2025, 2, 28)))
        def original = fixtures.template(cart, recurrenceType: MONTHLY, nextExecutionDate: LocalDate.of(2025, 3, 31), anchorDay: 31)

        and: "nur der Betrag ändert sich, Änderung gilt für alle zukünftigen Einträge"
        def dto = new CartDto(cart)
        dto.amount = 950d
        dto.templateUpdateSelected = true
        LOG.info('Szenario: heute {}, Gruppe {}, Einkauf {} Miete 900.00 vom 2025-02-28, Template {} MONTHLY anchorDay 31, ' +
                'nextExecutionDate 2025-03-31; neuer Betrag 950.00', testClock.today, wg.group.id, cart.id, original.id)

        when:
        cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, wg.category, 1)
        flushAndClear()
        def replacement = activeTemplate(wg)
        LOG.info('cartService.updateCartAndTemplateIfValid(Cart {}: amount, templateUpdate) -> aktives Template {}: ' +
                'anchorDay {}, nextExecutionDate {}', cart.id, replacement.id, replacement.anchorDay, replacement.nextExecutionDate)

        then:
        replacement.id != original.id
        replacement.anchorDay == 31
        replacement.nextExecutionDate == LocalDate.of(2025, 3, 31)
    }

    def "Template-Update mit geändertem Datum auf #neuesDatum übernimmt den Tag #erwarteterAnker als Ankertag"() {
        given: "Einkauf vom 31.03. aus einem Template vom 31."
        testClock.today = neuesDatum
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Miete', datePurchased: DateUtils.toDate(LocalDate.of(2025, 3, 31)))
        fixtures.template(cart, recurrenceType: MONTHLY, nextExecutionDate: LocalDate.of(2025, 4, 30), anchorDay: 31)

        and:
        def dto = new CartDto(cart)
        dto.datePurchased = DateUtils.toDate(neuesDatum)
        dto.templateUpdateSelected = true
        LOG.info('Szenario: heute {}, Gruppe {}, Einkauf {} Miete vom 2025-03-31, Template MONTHLY anchorDay 31; ' +
                'neues Datum {}', testClock.today, wg.group.id, cart.id, neuesDatum)

        when:
        cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, wg.category, 1)
        flushAndClear()
        def replacement = activeTemplate(wg)
        LOG.info('cartService.updateCartAndTemplateIfValid(Cart {}: datePurchased, templateUpdate) -> aktives Template {}: ' +
                'anchorDay {}, nextExecutionDate {}', cart.id, replacement.id, replacement.anchorDay, replacement.nextExecutionDate)

        then:
        replacement.anchorDay == erwarteterAnker
        replacement.nextExecutionDate == erwarteterTermin

        where:
        // 30.04. ist auch die gekappte Form von Anker 31; das geänderte Datum geht vor
        neuesDatum                || erwarteterAnker | erwarteterTermin
        LocalDate.of(2025, 4, 30) || 30              | LocalDate.of(2025, 5, 30)
        LocalDate.of(2025, 4, 15) || 15              | LocalDate.of(2025, 5, 15)
    }

    def "Bearbeiten eines gekappten Einkaufs ohne Änderung erkennt keine Template-Änderung"() {
        given:
        testClock.today = LocalDate.of(2025, 2, 28)
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Miete', datePurchased: DateUtils.toDate(LocalDate.of(2025, 2, 28)))
        def original = fixtures.template(cart, recurrenceType: MONTHLY, nextExecutionDate: LocalDate.of(2025, 3, 31), anchorDay: 31)

        and:
        def dto = new CartDto(cart)
        dto.templateUpdateSelected = true
        LOG.info('Szenario: heute {}, Gruppe {}, Einkauf {} Miete vom 2025-02-28, Template {} MONTHLY anchorDay 31, ' +
                'nextExecutionDate 2025-03-31; keine Änderung', testClock.today, wg.group.id, cart.id, original.id)

        when:
        def result = cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, wg.category, 1)
        flushAndClear()
        LOG.info('cartService.updateCartAndTemplateIfValid(Cart {}: templateUpdate) -> hasTemplateChanged {}', cart.id,
                result.hasTemplateChanged)

        then:
        !result.hasTemplateChanged
        def unchanged = activeTemplate(wg)
        unchanged.id == original.id
        unchanged.nextExecutionDate == LocalDate.of(2025, 3, 31)
    }

    def "Anderer Ankertag bei gleichem nächsten Termin erzeugt ein neues Template"() {
        given: "Einkauf vom 30.01. (Anker 30); am 31.01. ergeben Anker 30 und 31 beide den 28.02."
        testClock.today = LocalDate.of(2025, 1, 31)
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Miete', datePurchased: DateUtils.toDate(LocalDate.of(2025, 1, 30)))
        def original = fixtures.template(cart, recurrenceType: MONTHLY, nextExecutionDate: LocalDate.of(2025, 2, 28), anchorDay: 30)

        and: "Datum auf den 31.01., sonst unverändert"
        def dto = new CartDto(cart)
        dto.datePurchased = DateUtils.toDate(LocalDate.of(2025, 1, 31))
        dto.templateUpdateSelected = true
        LOG.info('Szenario: heute {}, Gruppe {}, Einkauf {} Miete vom 2025-01-30, Template {} MONTHLY anchorDay 30, ' +
                'nextExecutionDate 2025-02-28; neues Datum 2025-01-31', testClock.today, wg.group.id, cart.id, original.id)

        when:
        def result = cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, wg.category, 1)
        flushAndClear()
        LOG.info('cartService.updateCartAndTemplateIfValid(Cart {}: datePurchased, templateUpdate) -> hasTemplateChanged {}',
                cart.id, result.hasTemplateChanged)

        then:
        result.hasTemplateChanged
        def originalStillActive = templateRepository.findById(original.id).get().active
        !originalStillActive

        and:
        def replacement = activeTemplate(wg)
        replacement.anchorDay == 31
        replacement.nextExecutionDate == LocalDate.of(2025, 2, 28)
    }

    // ----- addCart (DIV-14): öffentlicher Ablauf mit Anmeldung, Prüfungen und Mitgliederzahl -----

    def "Mitglied legt einen Einkauf an: gespeichert mit Durchschnitt pro Mitglied"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def (groupId, benId) = [wg.group.id, ben.id]
        def dto = TestData.cartDto(groupId: groupId, categoryDto: TestData.categoryDto(wg.category), title: 'Pizza',
                amount: 40d, datePurchased: DateUtils.toDate(TestData.REFERENCE_DATE))
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Owner {}, Ben {} (angemeldet); Einkauf Pizza 40.00 am {}', groupId, wg.owner.id,
                benId, TestData.REFERENCE_DATE)

        when:
        def result = cartService.addCart(dto)
        flushAndClear()
        def saved = cartRepository.findById(result.id).get()
        LOG.info('cartService.addCart(Pizza 40.00) -> Cart {}: User {}, averagePerMember {}, recurrenceType {}', result.id,
                saved.user.id, saved.averagePerMember, result.recurrenceType)

        then: "Carts werden nicht live synchronisiert"
        0 * notificationService._

        and:
        saved.group.id == groupId
        saved.user.id == benId
        saved.title == 'Pizza'
        saved.amount == 40d
        saved.averagePerMember == 20d
        saved.template == null
        templateRepository.findByGroupId(groupId).isEmpty()
    }

    def "Nicht-Mitglied kann keinen Einkauf anlegen"() {
        given:
        def wg = fixtures.group()
        def fremder = fixtures.user()
        def groupId = wg.group.id
        def dto = TestData.cartDto(groupId: groupId, categoryDto: TestData.categoryDto(wg.category))
        flushAndClear()
        loginAs(fremder)
        LOG.info('Szenario: Gruppe {}, Owner {}, angemeldeter User {} ist kein Mitglied', groupId, wg.owner.id, fremder.id)

        when:
        LOG.info('cartService.addCart(Gruppe {}) als User {}, erwartet NotOwnerOrMemberOfGroupException', groupId, fremder.id)
        cartService.addCart(dto)

        then:
        thrown(NotOwnerOrMemberOfGroupException)
        cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(groupId).isEmpty()
    }

    def "Ausgetretenes Mitglied kann keinen Einkauf anlegen, auch nicht innerhalb seiner alten Mitgliedschaft"() {
        given: "Ehemaliger hat eine beendete Mitgliedschaft und steht nicht mehr in group.members"
        def wg = fixtures.group()
        def ehemalig = fixtures.user()
        def groupId = wg.group.id
        def austritt = TestData.REFERENCE_DATE.minusDays(10)
        fixtures.membership(wg, ehemalig, membershipStart: DateUtils.toDate(TestData.REFERENCE_DATE.minusYears(1)),
                membershipEnd: DateUtils.toDate(austritt))
        def kaufdatum = austritt.minusDays(10)
        def dto = TestData.cartDto(groupId: groupId, categoryDto: TestData.categoryDto(wg.category),
                datePurchased: DateUtils.toDate(kaufdatum))
        flushAndClear()
        loginAs(ehemalig)
        LOG.info('Szenario: Gruppe {}, Owner {}, Ehemaliger {} (angemeldet, Mitglied bis {}); Kaufdatum {}', groupId,
                wg.owner.id, ehemalig.id, austritt, kaufdatum)

        when:
        LOG.info('cartService.addCart(Gruppe {}, {}) als User {}, erwartet NotOwnerOrMemberOfGroupException', groupId,
                kaufdatum, ehemalig.id)
        cartService.addCart(dto)

        then:
        thrown(NotOwnerOrMemberOfGroupException)
        cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(groupId).isEmpty()
    }

    def "Kaufdatum vor Beginn der Mitgliedschaft wird abgelehnt, weder Einkauf noch Template werden gespeichert"() {
        given: "Ben ist seit REFERENCE_DATE - 1 Jahr Mitglied; der monatliche Einkauf liegt einen Tag davor"
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def kaufdatum = TestData.REFERENCE_DATE.minusYears(1).minusDays(1)
        def dto = TestData.cartDto(groupId: groupId, categoryDto: TestData.categoryDto(wg.category),
                datePurchased: DateUtils.toDate(kaufdatum), recurrenceType: MONTHLY)
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Owner {}, Ben {} (angemeldet, Mitglied seit {}); Kaufdatum {}, MONTHLY', groupId,
                wg.owner.id, ben.id, TestData.REFERENCE_DATE.minusYears(1), kaufdatum)

        when:
        LOG.info('cartService.addCart(Gruppe {}, {}) als Ben, erwartet DatePurchasedNotWithinMembershipPeriodException',
                groupId, kaufdatum)
        cartService.addCart(dto)

        then:
        thrown(DatePurchasedNotWithinMembershipPeriodException)
        cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(groupId).isEmpty()
        templateRepository.findByGroupId(groupId).isEmpty()
    }

    def "Wiederholung #typ legt ein aktives Template mit nächstem Termin #erwartet an"() {
        given: "Kaufdatum = heute (testClock)"
        def wg = fixtures.group()
        def (groupId, ownerId, categoryId) = [wg.group.id, wg.owner.id, wg.category.id]
        def dto = TestData.cartDto(groupId: groupId, categoryDto: TestData.categoryDto(wg.category), title: 'Miete',
                amount: 900d, datePurchased: DateUtils.toDate(testClock.today), recurrenceType: typ)
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: heute {}, Gruppe {}, Owner {} (angemeldet); Einkauf Miete 900.00 am {}, {}', testClock.today,
                groupId, ownerId, testClock.today, typ)

        when:
        def result = cartService.addCart(dto)
        flushAndClear()
        def templates = templateRepository.findByGroupId(groupId)
        def cartTemplateId = cartRepository.findById(result.id).get().template.id
        LOG.info('cartService.addCart(Miete, {}) -> Cart {}, Templates {}: nextExecutionDate {}, active {}', typ, result.id,
                templates*.id, templates*.nextExecutionDate, templates*.active)

        then:
        templates.size() == 1
        def template = templates.first()
        template.active
        template.recurrenceType == typ
        template.nextExecutionDate == erwartet
        template.startDate == testClock.today
        template.title == 'Miete'
        template.amount == 900d
        template.userId == ownerId
        template.groupId == groupId
        template.categoryId == categoryId
        cartTemplateId == template.id

        and: "das zurückgegebene DTO zeigt das Template"
        result.recurrenceType == typ
        result.hasActiveTemplate
        result.templateId == template.id

        where:
        typ     || erwartet
        DAILY   || LocalDate.of(2025, 6, 16)
        WEEKLY  || LocalDate.of(2025, 6, 22)
        MONTHLY || LocalDate.of(2025, 7, 15)
        YEARLY  || LocalDate.of(2026, 6, 15)
    }

    def "Ohne Wiederholung (#typ) legt addCart kein Template an"() {
        given:
        def wg = fixtures.group()
        def groupId = wg.group.id
        def dto = TestData.cartDto(groupId: groupId, categoryDto: TestData.categoryDto(wg.category), recurrenceType: typ)
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet); Einkauf mit recurrenceType {}', groupId, wg.owner.id, typ)

        when:
        def result = cartService.addCart(dto)
        flushAndClear()
        def templates = templateRepository.findByGroupId(groupId)
        LOG.info('cartService.addCart(recurrenceType {}) -> Cart {}, recurrenceType {}, Templates {}', typ, result.id,
                result.recurrenceType, templates*.id)

        then:
        templates.isEmpty()
        result.recurrenceType == NONE
        !result.hasActiveTemplate

        where:
        typ << [NONE, null]
    }

    def "Mitgliederzahl zum Kaufdatum: #fall ergibt Durchschnitt #erwartet"() {
        given: """Owner (seit REFERENCE_DATE - 1 Jahr) legt am REFERENCE_DATE an; Bens Zeitraum liegt auf der Grenze.
                  Prüft die SQL-Zählung (inklusiv). 0 Mitglieder ist über addCart nicht erreichbar, weil der anlegende
                  Nutzer selbst Mitglied sein muss; Query bei 0: DataLoaderServiceIntegrationSpec 'Ohne Mitgliedschaft
                  zum Kaufdatum ist die Mitgliederzahl 0', Mapper bei 0: CartDtoMapperSpec 'Durchschnitt pro Mitglied
                  ist 0 bei 0 Mitgliedern' (DIV-21)."""
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def benMembership = membershipRepository.findByGroupIdAndUserId(groupId, ben.id).first()
        if (benStart) benMembership.membershipStart = DateUtils.toDate(benStart)
        benMembership.membershipEnd = benEnde ? DateUtils.toDate(benEnde) : null
        membershipRepository.save(benMembership)
        def dto = TestData.cartDto(groupId: groupId, categoryDto: TestData.categoryDto(wg.category), amount: 40d,
                datePurchased: DateUtils.toDate(TestData.REFERENCE_DATE))
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet), Ben {} (Beginn {}, Ende {}); Einkauf 40.00 am {}', groupId,
                wg.owner.id, ben.id, benStart ?: 'Standard', benEnde ?: 'offen', TestData.REFERENCE_DATE)

        when:
        def result = cartService.addCart(dto)
        flushAndClear()
        def average = cartRepository.findById(result.id).get().averagePerMember
        LOG.info('cartService.addCart(40.00 am {}) -> Cart {}, averagePerMember {}', TestData.REFERENCE_DATE, result.id,
                average)

        then:
        average == erwartet

        where:
        fall                            | benStart                            | benEnde                              || erwartet
        'Kauf am Eintrittstag'          | TestData.REFERENCE_DATE             | null                                 || 20d
        'Kauf am Tag vor dem Eintritt'  | TestData.REFERENCE_DATE.plusDays(1) | null                                 || 40d
        'Kauf am Austrittstag'          | null                                | TestData.REFERENCE_DATE              || 20d
        'Kauf am Tag nach dem Austritt' | null                                | TestData.REFERENCE_DATE.minusDays(1) || 40d
    }

    // Grenztage des anlegenden Nutzers (Java-Verifikation, inklusiv): Ben war bis 20.05.2025 Mitglied, ist ab
    // 01.06.2025 wieder Mitglied und steht weiter in group.members. Keine offene Historie überdeckt die Lücke.

    def "Kaufdatum am #fall wird angenommen"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def aktuell = membershipRepository.findByGroupIdAndUserId(groupId, ben.id).first()
        aktuell.membershipStart = DateUtils.toDate(LocalDate.of(2025, 6, 1))
        membershipRepository.save(aktuell)
        fixtures.membership(wg, ben, membershipStart: DateUtils.toDate(LocalDate.of(2024, 6, 15)),
                membershipEnd: DateUtils.toDate(LocalDate.of(2025, 5, 20)))
        def dto = TestData.cartDto(groupId: groupId, categoryDto: TestData.categoryDto(wg.category), amount: 40d,
                datePurchased: DateUtils.toDate(kaufdatum))
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Owner {}, Ben {} (angemeldet, Mitglied 2024-06-15..2025-05-20 und ab 2025-06-01); ' +
                'Einkauf 40.00 am {}', groupId, wg.owner.id, ben.id, kaufdatum)

        when:
        def result = cartService.addCart(dto)
        flushAndClear()
        def average = cartRepository.findById(result.id).get().averagePerMember
        LOG.info('cartService.addCart(40.00 am {}) als Ben -> Cart {}, averagePerMember {}', kaufdatum, result.id, average)

        then: "Owner und Ben zählen am Grenztag mit"
        average == 20d

        where:
        fall                                             | kaufdatum
        'letzten Tag der früheren eigenen Mitgliedschaft' | LocalDate.of(2025, 5, 20)
        'ersten Tag der neuen eigenen Mitgliedschaft'     | LocalDate.of(2025, 6, 1)
    }

    def "Kaufdatum am #fall zwischen zwei Mitgliedschaften wird abgelehnt, nichts gespeichert"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def aktuell = membershipRepository.findByGroupIdAndUserId(groupId, ben.id).first()
        aktuell.membershipStart = DateUtils.toDate(LocalDate.of(2025, 6, 1))
        membershipRepository.save(aktuell)
        fixtures.membership(wg, ben, membershipStart: DateUtils.toDate(LocalDate.of(2024, 6, 15)),
                membershipEnd: DateUtils.toDate(LocalDate.of(2025, 5, 20)))
        def dto = TestData.cartDto(groupId: groupId, categoryDto: TestData.categoryDto(wg.category),
                datePurchased: DateUtils.toDate(kaufdatum))
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Owner {}, Ben {} (angemeldet, Mitglied 2024-06-15..2025-05-20 und ab 2025-06-01); ' +
                'Kaufdatum {}', groupId, wg.owner.id, ben.id, kaufdatum)

        when:
        LOG.info('cartService.addCart(am {}) als Ben, erwartet DatePurchasedNotWithinMembershipPeriodException', kaufdatum)
        cartService.addCart(dto)

        then:
        thrown(DatePurchasedNotWithinMembershipPeriodException)
        cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(groupId).isEmpty()

        where:
        fall                    | kaufdatum
        'ersten Tag der Lücke'  | LocalDate.of(2025, 5, 21)
        'letzten Tag der Lücke' | LocalDate.of(2025, 5, 31)
    }

    // ----- updateCart (DIV-14): öffentlicher Ablauf mit Anmeldung und Prüfungen -----
    // Die Requests senden immer die groupId des Einkaufs (Bug-Kandidat B1, Gruppenwechsel per DTO, bewusst nicht getestet).

    def "Eigentümer bearbeitet seinen Einkauf: Werte und Durchschnitt aus der Mitgliederzahl gespeichert"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def cart = fixtures.cart(wg, title: 'Einkauf', amount: 40d, averagePerMember: 20d)
        def dto = new CartDto(cart)
        dto.title = 'Großeinkauf'
        dto.amount = 60d
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet, Eigentümer), Ben {}; Einkauf {} 40.00 -> Großeinkauf 60.00',
                wg.group.id, wg.owner.id, ben.id, cart.id)

        when:
        def result = cartService.updateCart(dto)
        flushAndClear()
        def saved = cartRepository.findById(cart.id).get()
        LOG.info('cartService.updateCart(Cart {}: Großeinkauf 60.00) -> title {}, amount {}, averagePerMember {}', cart.id,
                result.title, saved.amount, saved.averagePerMember)

        then:
        result.title == 'Großeinkauf'
        saved.title == 'Großeinkauf'
        saved.amount == 60d
        saved.averagePerMember == 30d
    }

    def "#rolle kann den Einkauf eines anderen nicht bearbeiten"() {
        given: "Eigentümer des Einkaufs ist jeweils der andere Nutzer der Gruppe"
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def (eigentuemer, bearbeiter) = rolle == 'Mitglied' ? [wg.owner, ben] : [ben, wg.owner]
        def cart = fixtures.cart(wg, user: eigentuemer, title: 'Einkauf', amount: 40d)
        def dto = new CartDto(cart)
        dto.amount = 99d
        flushAndClear()
        loginAs(bearbeiter)
        LOG.info('Szenario: Gruppe {}, Owner {}, Ben {}; Einkauf {} von User {}, angemeldet User {} ({})', wg.group.id,
                wg.owner.id, ben.id, cart.id, eigentuemer.id, bearbeiter.id, rolle)

        when:
        LOG.info('cartService.updateCart(Cart {}: amount 99.00) als User {}, erwartet NotOwnerOfCartException', cart.id,
                bearbeiter.id)
        cartService.updateCart(dto)

        then:
        thrown(NotOwnerOfCartException)
        def savedAmount = cartRepository.findById(cart.id).get().amount
        savedAmount == 40d

        where:
        rolle << ['Mitglied', 'Gruppen-Owner']
    }

    def "Nicht-Mitglied kann keinen Einkauf bearbeiten"() {
        given:
        def wg = fixtures.group()
        def fremder = fixtures.user()
        def cart = fixtures.cart(wg, amount: 40d)
        def dto = new CartDto(cart)
        dto.amount = 99d
        flushAndClear()
        loginAs(fremder)
        LOG.info('Szenario: Gruppe {}, Owner {}, Einkauf {} vom Owner; angemeldeter User {} ist kein Mitglied', wg.group.id,
                wg.owner.id, cart.id, fremder.id)

        when:
        LOG.info('cartService.updateCart(Cart {}) als User {}, erwartet NotOwnerOrMemberOfGroupException', cart.id, fremder.id)
        cartService.updateCart(dto)

        then:
        thrown(NotOwnerOrMemberOfGroupException)
        def savedAmount = cartRepository.findById(cart.id).get().amount
        savedAmount == 40d
    }

    def "Neues Kaufdatum außerhalb der Mitgliedschaft wird abgelehnt, Einkauf und Template bleiben unverändert"() {
        given: "Owner ist seit REFERENCE_DATE - 1 Jahr Mitglied; neues Datum einen Tag davor"
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, amount: 40d)
        def template = fixtures.template(cart, recurrenceType: MONTHLY)
        def neuesDatum = TestData.REFERENCE_DATE.minusYears(1).minusDays(1)
        def dto = new CartDto(cart)
        dto.datePurchased = DateUtils.toDate(neuesDatum)
        dto.templateUpdateSelected = true
        def (groupId, templateId) = [wg.group.id, template.id]
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet, Mitglied seit {}), Einkauf {} vom {}, Template {} MONTHLY; ' +
                'neues Datum {}', groupId, wg.owner.id, TestData.REFERENCE_DATE.minusYears(1), cart.id,
                TestData.REFERENCE_DATE, templateId, neuesDatum)

        when:
        LOG.info('cartService.updateCart(Cart {}: datePurchased {}, templateUpdate), erwartet ' +
                'DatePurchasedNotWithinMembershipPeriodException', cart.id, neuesDatum)
        cartService.updateCart(dto)

        then:
        thrown(DatePurchasedNotWithinMembershipPeriodException)
        def savedDate = DateUtils.toLocalDate(cartRepository.findById(cart.id).get().datePurchased)
        savedDate == TestData.REFERENCE_DATE
        def templates = templateRepository.findByGroupId(groupId)
        templates*.id == [templateId]
        templates.every { it.active }
    }

    def "Template-Update über updateCart ersetzt das Template"() {
        given:
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Miete', amount: 900d)
        def original = fixtures.template(cart, recurrenceType: MONTHLY)
        def dto = new CartDto(cart)
        dto.amount = 950d
        dto.templateUpdateSelected = true
        def (groupId, originalId) = [wg.group.id, original.id]
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: heute {}, Gruppe {}, Owner {} (angemeldet), Einkauf {} Miete 900.00 vom {}, Template {} MONTHLY; ' +
                'neuer Betrag 950.00 für alle zukünftigen Einträge', testClock.today, groupId, wg.owner.id, cart.id,
                TestData.REFERENCE_DATE, originalId)

        when:
        def result = cartService.updateCart(dto)
        flushAndClear()
        def alt = templateRepository.findById(originalId).get()
        def cartTemplateId = cartRepository.findById(cart.id).get().template.id
        LOG.info('cartService.updateCart(Cart {}: amount 950.00, templateUpdate) -> hasTemplateChanged {}, Template {} ' +
                'active {}, endDate {}; Einkauf verknüpft mit Template {}', cart.id, result.hasTemplateChanged, originalId,
                alt.active, alt.endDate, cartTemplateId)

        then:
        result.hasTemplateChanged
        !alt.active
        alt.endDate == testClock.today

        and: "genau ein neues aktives Template mit dem neuen Betrag, am Einkauf verknüpft"
        def neu = activeTemplate(wg)
        neu.id != originalId
        neu.amount == 950d
        neu.recurrenceType == MONTHLY
        neu.nextExecutionDate == LocalDate.of(2025, 7, 15)
        cartTemplateId == neu.id
    }

    def "Wiederholung auf NONE über updateCart deaktiviert das Template"() {
        given:
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Miete')
        def original = fixtures.template(cart, recurrenceType: MONTHLY)
        def dto = new CartDto(cart)
        dto.recurrenceType = NONE
        def (groupId, originalId) = [wg.group.id, original.id]
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: heute {}, Gruppe {}, Owner {} (angemeldet), Einkauf {}, Template {} MONTHLY; neue Wiederholung NONE',
                testClock.today, groupId, wg.owner.id, cart.id, originalId)

        when:
        def result = cartService.updateCart(dto)
        flushAndClear()
        def alt = templateRepository.findById(originalId).get()
        LOG.info('cartService.updateCart(Cart {}: recurrenceType NONE) -> recurrenceType {}, Template {} active {}, endDate {}',
                cart.id, result.recurrenceType, originalId, alt.active, alt.endDate)

        then:
        result.recurrenceType == NONE
        !alt.active
        alt.endDate == testClock.today
        activeTemplateTypes(wg).isEmpty()
    }

    // ----- deleteCart (DIV-14) -----
    // Rollback-Spec: Service und Test teilen den Persistence Context. Geprüft wird die Logik der Template-Deaktivierung,
    // nicht ihr Persistieren ohne eigene Transaktion (deleteCart ist nicht @Transactional, Bug-Kandidat B3).

    def "Eigentümer löscht seinen Einkauf ohne Template"() {
        given:
        def wg = fixtures.group()
        def cart = fixtures.cart(wg)
        def cartId = cart.id
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet, Eigentümer), Einkauf {} ohne Template', wg.group.id, wg.owner.id,
                cartId)

        when:
        cartService.deleteCart(cartId)
        flushAndClear()
        def stillThere = cartRepository.findById(cartId).isPresent()
        LOG.info('cartService.deleteCart(Cart {}) -> noch vorhanden {}', cartId, stillThere)

        then:
        !stillThere
    }

    def "Löschen des einzigen Einkaufs eines Templates deaktiviert das Template"() {
        given:
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Miete')
        def template = fixtures.template(cart, recurrenceType: MONTHLY)
        def (cartId, templateId) = [cart.id, template.id]
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: heute {}, Gruppe {}, Owner {} (angemeldet), Einkauf {} mit Template {} MONTHLY (einziger Einkauf)',
                testClock.today, wg.group.id, wg.owner.id, cartId, templateId)

        when:
        cartService.deleteCart(cartId)
        flushAndClear()
        def stillThere = cartRepository.findById(cartId).isPresent()
        def saved = templateRepository.findById(templateId).get()
        LOG.info('cartService.deleteCart(Cart {}) -> noch vorhanden {}, Template {} active {}, endDate {}', cartId, stillThere,
                templateId, saved.active, saved.endDate)

        then:
        !stillThere
        !saved.active
        saved.endDate == testClock.today
    }

    def "Löschen eines von zwei Einkäufen desselben Templates lässt das Template aktiv"() {
        given:
        def wg = fixtures.group()
        def erster = fixtures.cart(wg, title: 'Miete')
        def template = fixtures.template(erster, recurrenceType: MONTHLY)
        def zweiter = fixtures.cart(wg, title: 'Miete', template: template,
                datePurchased: DateUtils.toDate(TestData.REFERENCE_DATE.minusMonths(1)))
        def (ersterId, zweiterId, templateId) = [erster.id, zweiter.id, template.id]
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet), Einkäufe {} und {} aus Template {} MONTHLY; gelöscht wird {}',
                wg.group.id, wg.owner.id, ersterId, zweiterId, templateId, ersterId)

        when:
        cartService.deleteCart(ersterId)
        flushAndClear()
        def saved = templateRepository.findById(templateId).get()
        def zweiterVorhanden = cartRepository.findById(zweiterId).isPresent()
        LOG.info('cartService.deleteCart(Cart {}) -> Template {} active {}, endDate {}; Einkauf {} vorhanden {}', ersterId,
                templateId, saved.active, saved.endDate, zweiterId, zweiterVorhanden)

        then:
        saved.active
        saved.endDate == null
        zweiterVorhanden
        !cartRepository.findById(ersterId).isPresent()
    }

    def "#rolle kann den Einkauf eines anderen nicht löschen, Einkauf und Template bleiben erhalten"() {
        given: "Eigentümer des Einkaufs ist jeweils der andere Nutzer der Gruppe"
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def (eigentuemer, loescher) = rolle == 'Mitglied' ? [wg.owner, ben] : [ben, wg.owner]
        def cart = fixtures.cart(wg, user: eigentuemer)
        def template = fixtures.template(cart, recurrenceType: MONTHLY)
        def (cartId, templateId) = [cart.id, template.id]
        flushAndClear()
        loginAs(loescher)
        LOG.info('Szenario: Gruppe {}, Owner {}, Ben {}; Einkauf {} von User {} mit Template {}, angemeldet User {} ({})',
                wg.group.id, wg.owner.id, ben.id, cartId, eigentuemer.id, templateId, loescher.id, rolle)

        when:
        LOG.info('cartService.deleteCart(Cart {}) als User {}, erwartet NotOwnerOfCartException', cartId, loescher.id)
        cartService.deleteCart(cartId)

        then:
        thrown(NotOwnerOfCartException)
        cartRepository.findById(cartId).isPresent()
        def templateActive = templateRepository.findById(templateId).get().active
        templateActive

        where:
        rolle << ['Mitglied', 'Gruppen-Owner']
    }

    def "Nicht-Mitglied kann keinen Einkauf löschen"() {
        given:
        def wg = fixtures.group()
        def fremder = fixtures.user()
        def cart = fixtures.cart(wg)
        def cartId = cart.id
        flushAndClear()
        loginAs(fremder)
        LOG.info('Szenario: Gruppe {}, Owner {}, Einkauf {} vom Owner; angemeldeter User {} ist kein Mitglied', wg.group.id,
                wg.owner.id, cartId, fremder.id)

        when:
        LOG.info('cartService.deleteCart(Cart {}) als User {}, erwartet NotOwnerOrMemberOfGroupException', cartId, fremder.id)
        cartService.deleteCart(cartId)

        then:
        thrown(NotOwnerOrMemberOfGroupException)
        cartRepository.findById(cartId).isPresent()
    }

    private CartTemplate activeTemplate(GroupScenario wg) {
        def active = templateRepository.findByGroupId(wg.group.id).findAll { it.active }
        assert active.size() == 1
        active.first()
    }

    private List<RecurrenceType> activeTemplateTypes(GroupScenario wg) {
        templateRepository.findByGroupId(wg.group.id).findAll { it.active }*.recurrenceType
    }
}
