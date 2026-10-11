package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.constants.RecurrenceType
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto
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

    def "Bearbeiten #originalRecurrenceType → #updatedRecurrenceType mit Titel '#newTitle' (Template-Update: #updateSelected) hinterlässt aktive Templates #expectedActiveTypes"() {
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
        result.recurrenceType == updatedRecurrenceType
        cartRepository.findById(cart.id).get().title == expectedTitle

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

    def "Wiederholung #oldType → #newType: altes Template aktiv = #originalActive, aktive Templates #activeTypes"() {
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
        templateRepository.findById(original.id).get().active == originalActive
        activeTemplateTypes(wg) == activeTypes

        where:
        oldType | newType || originalActive | activeTypes
        MONTHLY | MONTHLY || true           | [MONTHLY]
        MONTHLY | NONE    || false          | []
        MONTHLY | DAILY   || false          | [DAILY]
        DAILY   | WEEKLY  || false          | [WEEKLY]
        WEEKLY  | WEEKLY  || true           | [WEEKLY]
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
        cartRepository.findById(cart.id).get().template?.recurrenceType == expectedTypes[0]

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
        given: "Kaufdatum relativ zu heute: calculateNextDate rechnet ab LocalDate.now() (bis T6/Clock)"
        def wg = fixtures.group()
        def purchased = LocalDate.now().minusDays(3)
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
        !templateRepository.findById(original.id).get().active

        and: "genau ein aktives, neues Template"
        def active = templateRepository.findByGroupId(wg.group.id).findAll { it.active }
        active.size() == 1
        def replacement = active.first()
        replacement.recurrenceType == WEEKLY
        replacement.title == 'Electricity'
        replacement.amount == 80d
        replacement.nextExecutionDate == purchased.plusWeeks(1)
        replacement.userId == wg.owner.id
        replacement.groupId == wg.group.id
        replacement.categoryId == wg.category.id
        replacement.id == cartRepository.findById(cart.id).get().template.id
    }

    // Scheduler-Fälle: generateRecurringCarts vergleicht mit dem echten Datum, daher bis T6 (Clock) relativ zu LocalDate.now().

    def "Scheduler erzeugt keinen Einkauf, wenn das Template noch nicht fällig ist"() {
        given:
        def tomorrow = LocalDate.now().plusDays(1)
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Gym', amount: 30d, datePurchased: DateUtils.toDate(tomorrow))
        def template = fixtures.template(cart, nextExecutionDate: tomorrow)

        when:
        cartService.generateRecurringCarts()
        flushAndClear()

        then: "kein Einkauf erzeugt"
        cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id).size() == 1

        and: "Template unverändert und aktiv"
        def reloaded = templateRepository.findById(template.id).get()
        reloaded.nextExecutionDate == tomorrow
        reloaded.active
    }

    def "Scheduler erzeugt kein Duplikat, wenn ein gleicher Einkauf schon existiert"() {
        given:
        def today = LocalDate.now()
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Netflix', amount: 12.99d, datePurchased: DateUtils.toDate(today))
        def template = fixtures.template(cart, nextExecutionDate: today)

        when:
        cartService.generateRecurringCarts()
        flushAndClear()

        then: "kein weiterer Einkauf"
        cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id).size() == 1

        and: "Template auf den nächsten Termin verschoben"
        templateRepository.findById(template.id).get().nextExecutionDate == today.plusMonths(1)
    }

    def "Scheduler erzeugt einen neuen Einkauf aus dem fälligen Template"() {
        given:
        def today = LocalDate.now()
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Spotify', amount: 9.99d, datePurchased: DateUtils.toDate(today.minusDays(1)))
        def template = fixtures.template(cart, nextExecutionDate: today)

        when:
        cartService.generateRecurringCarts()
        flushAndClear()

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

    def "Scheduler erzeugt bei #recurrenceType einen Einkauf und setzt den nächsten Termin auf #expectedNextDate"() {
        given:
        def today = LocalDate.now()
        def wg = fixtures.group()
        def cart = fixtures.cart(wg, title: 'Gym', amount: 29.99d, datePurchased: DateUtils.toDate(today.minusDays(1)))
        def template = fixtures.template(cart, recurrenceType: recurrenceType, nextExecutionDate: today)

        when:
        cartService.generateRecurringCarts()
        flushAndClear()

        then: "Original und neuer Einkauf"
        cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id).size() == 2

        and:
        templateRepository.findById(template.id).get().nextExecutionDate == expectedNextDate

        where:
        recurrenceType || expectedNextDate
        DAILY          || LocalDate.now().plusDays(1)
        WEEKLY         || LocalDate.now().plusWeeks(1)
        MONTHLY        || LocalDate.now().plusMonths(1)
        YEARLY         || LocalDate.now().plusYears(1)
    }

    private List<RecurrenceType> activeTemplateTypes(GroupScenario wg) {
        templateRepository.findByGroupId(wg.group.id).findAll { it.active }*.recurrenceType
    }
}
