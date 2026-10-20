package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.constants.RecurrenceType
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto
import com.github.svenfran.budgetapp.budgetappbackend.entity.CartTemplate
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartTemplateRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.Fixtures.GroupScenario
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.springframework.beans.factory.annotation.Autowired

import java.time.LocalDate

import static com.github.svenfran.budgetapp.budgetappbackend.constants.RecurrenceType.*

class CartServiceIntegrationSpec extends IntegrationSpec {

    @Autowired CartService cartService
    @Autowired CartRepository cartRepository
    @Autowired CartTemplateRepository templateRepository

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

        when:
        def result = cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, wg.category, 1)
        flushAndClear()

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

        when:
        // groupMemberCount 2 = Owner + Ben (in CartService.updateCart aus der Mitgliedschaftshistorie ermittelt)
        cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, category, 2)
        flushAndClear()

        then:
        def saved = cartRepository.findById(cart.id).get()
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

        when:
        cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, wg.category, 1)
        flushAndClear()

        then:
        def originalStillActive = templateRepository.findById(original.id).get().active
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

        when:
        cartService.createTemplateForCartIfRecurrenceTypeValid(cart, wg.owner, wg.group, inputRecurrenceType)
        flushAndClear()

        then:
        def templates = templateRepository.findByGroupId(wg.group.id)
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

        when:
        def result = cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, wg.category, 1)
        flushAndClear()

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

        when:
        def result = cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, wg.category, 1)
        flushAndClear()

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

        when:
        def result = cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, wg.category, 1)
        flushAndClear()

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

        when:
        cartService.createTemplateForCartIfRecurrenceTypeValid(cart, wg.owner, wg.group, MONTHLY)
        flushAndClear()

        then:
        def template = templateRepository.findByGroupId(wg.group.id).first()
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

        when:
        cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, wg.category, 1)
        flushAndClear()

        then:
        def replacement = activeTemplate(wg)
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

        when:
        cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, wg.category, 1)
        flushAndClear()

        then:
        def replacement = activeTemplate(wg)
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

        when:
        def result = cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, wg.category, 1)
        flushAndClear()

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

        when:
        def result = cartService.updateCartAndTemplateIfValid(cart, dto, wg.owner, wg.group, wg.category, 1)
        flushAndClear()

        then:
        result.hasTemplateChanged
        def originalStillActive = templateRepository.findById(original.id).get().active
        !originalStillActive

        and:
        def replacement = activeTemplate(wg)
        replacement.anchorDay == 31
        replacement.nextExecutionDate == LocalDate.of(2025, 2, 28)
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
