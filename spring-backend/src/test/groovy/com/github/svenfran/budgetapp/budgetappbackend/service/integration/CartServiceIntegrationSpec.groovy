package com.github.svenfran.budgetapp.budgetappbackend.service.integration

import com.github.svenfran.budgetapp.budgetappbackend.constants.RecurrenceType
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto
import com.github.svenfran.budgetapp.budgetappbackend.repository.*
import com.github.svenfran.budgetapp.budgetappbackend.service.CartService
import com.github.svenfran.budgetapp.budgetappbackend.service.TestDataFactory
import com.github.svenfran.budgetapp.budgetappbackend.service.container.TestContainerEnv
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

import java.time.LocalDate

@Transactional
class CartServiceIntegrationSpec extends TestContainerEnv {

    @Autowired CartService cartService
    @Autowired CartRepository cartRepository
    @Autowired CartTemplateRepository templateRepository
    @Autowired CategoryRepository categoryRepository
    @Autowired UserRepository userRepository
    @Autowired GroupRepository groupRepository
    @Autowired GroupMembershipHistoryRepository gmhRepository

    def setup() {
        templateRepository.deleteAll()
        cartRepository.deleteAll()
        categoryRepository.deleteAll()
        groupRepository.deleteAll()
        userRepository.deleteAll()
    }

    def "updateCartAndTemplateIfValid - handles all cases correctly"() {
        given: "Persisted user, group, category and initial cart with template"
        def user = userRepository.save(TestDataFactory.buildUser())
        def group = groupRepository.save(TestDataFactory.buildGroup())
        def category = categoryRepository.save(TestDataFactory.buildCategory(group))
        def cart = cartRepository.save(TestDataFactory.buildCart(user, group, category, "Lunch", 100))
        def template = templateRepository.save(TestDataFactory.buildTemplate(cart, RecurrenceType.MONTHLY, TestDataFactory.asLocalDate(cart.datePurchased)))
        cart.setDescription("Weekly lunch")
        cart.setTemplate(template)
        cartRepository.save(cart)

        and: "Prepare updated CartDto"
        def dto = new CartDto(cart)
        dto.setTitle(newTitle ?: cart.title)
        dto.setRecurrenceType(updatedRecurrenceType)
        dto.setTemplateUpdateSelected(updateSelected)

        when: "Calling service under test"
        def result = cartService.updateCartAndTemplateIfValid(cart, dto, user, group, category, 1)

        then: "Validate expectations"
        result.title == expectedTitle
        result.recurrenceType == updatedRecurrenceType

        and: "Template handling"
        def allTemplates = templateRepository.findByActiveTrue()
        allTemplates.size() == expectedTemplateCount
        allTemplates.any { it.active } == expectedActiveTemplate

        where:
        originalRecurrenceType | updatedRecurrenceType  | newTitle        | updateSelected || expectedTitle   | expectedTemplateCount | expectedActiveTemplate
        RecurrenceType.MONTHLY | RecurrenceType.NONE    | "Lunch"         | false          || "Lunch"         | 0                     | false
        RecurrenceType.MONTHLY | RecurrenceType.WEEKLY  | "Lunch"         | false          || "Lunch"         | 1                     | true
        RecurrenceType.NONE    | RecurrenceType.NONE    | "Lunch Updated" | false          || "Lunch Updated" | 0                     | false
        RecurrenceType.MONTHLY | RecurrenceType.MONTHLY | "Lunch"         | true           || "Lunch"         | 1                     | true
        RecurrenceType.MONTHLY | RecurrenceType.MONTHLY | "Lunch Updated" | true           || "Lunch Updated" | 1                     | true
        RecurrenceType.DAILY   | RecurrenceType.NONE    | "Lunch"         | true           || "Lunch"         | 0                     | false
        RecurrenceType.DAILY   | RecurrenceType.NONE    | "Lunch Updated" | true           || "Lunch Updated" | 0                     | false
    }

    def "createTemplateForCartIfRecurrenceTypeValid - handles all cases correctly"() {
        given: "Persisted user, group, category"
        def user = userRepository.save(TestDataFactory.buildUser())
        def group = groupRepository.save(TestDataFactory.buildGroup())
        def category = categoryRepository.save(TestDataFactory.buildCategory(group))
        def cart = cartRepository.save(TestDataFactory.buildCart(user, group, category, "Lunch", 100))
        cart.setDescription("Weekly lunch")
        cartRepository.save(cart)

        and: "Prepare CartDto"
        def cartDto = new CartDto(cart)
        cartDto.setRecurrenceType(expectedRecurrenceType)

        when: "Calling service under test"
        cartService.createTemplateForCartIfRecurrenceTypeValid(cart, user, group, cartDto.recurrenceType)
        def templates = templateRepository.findByActiveTrue()

        then: "Validate expectations"
        if (expectedTemplateCreated) {
            assert templates.size() == 1
            def template = templates.first()
            template.recurrenceType == expectedRecurrenceType
            template.active == expectedActiveTemplate
        } else {
            assert templates.isEmpty()
        }

        where:
        inputRecurrenceType    || expectedTemplateCreated | expectedRecurrenceType | expectedActiveTemplate
        RecurrenceType.NONE    || false                   | null                   | null
        RecurrenceType.WEEKLY  || true                    | RecurrenceType.WEEKLY  | true
        RecurrenceType.MONTHLY || true                    | RecurrenceType.MONTHLY | true
        RecurrenceType.DAILY   || true                    | RecurrenceType.DAILY   | true
        RecurrenceType.YEARLY  || true                    | RecurrenceType.YEARLY  | true
    }

    def "updateCart_shouldUpdateTitleAndAmountCorrectly"() {
        given: "Persisted user, group, category, and cart"
        def user = userRepository.save(TestDataFactory.buildUser())
        def group = groupRepository.save(TestDataFactory.buildGroup())
        def category = categoryRepository.save(TestDataFactory.buildCategory(group))
        def cart = cartRepository.save(TestDataFactory.buildCart(user, group, category, "Groceries", 50))

        and: "CartDto with updated fields"
        def dto = new CartDto(cart)
        dto.setTitle("Weekly Groceries")
        dto.setDescription("desc of Weekly Groceries")
        dto.setAmount(75)

        when: "Calling service"
        def result = cartService.updateCartAndTemplateIfValid(cart, dto, user, group, category, 1)

        then: "Cart fields are updated correctly"
        result.title == "Weekly Groceries"
        result.amount == 75
        result.description == "desc of Weekly Groceries"
        result.recurrenceType == RecurrenceType.NONE

        and: "No template created"
        templateRepository.findAll().isEmpty()
    }

    def "updateCart_shouldNotCreateTemplateIfNoRecurrence"() {
        given: "Persisted user, group, category, and cart without recurrence"
        def user = userRepository.save(TestDataFactory.buildUser())
        def group = groupRepository.save(TestDataFactory.buildGroup())
        def category = categoryRepository.save(TestDataFactory.buildCategory(group))
        def cart = cartRepository.save(TestDataFactory.buildCart(user, group, category, "Books", 30))

        and: "CartDto also without recurrence"
        def dto = new CartDto(cart)
        dto.setRecurrenceType(RecurrenceType.NONE)

        when: "Calling service"
        def result = cartService.updateCartAndTemplateIfValid(cart, dto, user, group, category, 1)

        then: "Cart updated but no template created"
        result.title == "Books"
        result.recurrenceType == RecurrenceType.NONE
        templateRepository.findAll().isEmpty()
    }

    def "updateCart_shouldReplaceTemplateWhenRecurrenceChanges"() {
        given: "Cart with existing monthly template"
        def user = userRepository.save(TestDataFactory.buildUser())
        def group = groupRepository.save(TestDataFactory.buildGroup())
        def category = categoryRepository.save(TestDataFactory.buildCategory(group))
        def cart = cartRepository.save(TestDataFactory.buildCart(user, group, category, "Electricity", 80))

        def template = templateRepository.save(TestDataFactory.buildTemplate(cart, RecurrenceType.MONTHLY, TestDataFactory.asLocalDate(cart.datePurchased)))
        cart.setTemplate(template)
        cartRepository.save(cart)

        and: "DTO that changes recurrence type"
        def dto = new CartDto(cart)
        dto.setRecurrenceType(RecurrenceType.WEEKLY)

        when: "Calling service"
        def result = cartService.updateCartAndTemplateIfValid(cart, dto, user, group, category, 1)
        def templates = templateRepository.findByActiveTrue()

        then: "Cart recurrence updated, old template replaced"
        result.recurrenceType == RecurrenceType.WEEKLY
        templates.size() == 1

        and: "Template fields updated"
        def updatedTemplate = templates.first()
        updatedTemplate.recurrenceType == RecurrenceType.WEEKLY
        updatedTemplate.title == "Electricity"
        updatedTemplate.amount == 80
        updatedTemplate.active
        updatedTemplate.nextExecutionDate == TestDataFactory.asLocalDate(cart.datePurchased).plusWeeks(1)
        updatedTemplate.userId == user.id
        updatedTemplate.groupId == group.id
        updatedTemplate.categoryId == category.id
        updatedTemplate.id == cart.template.id
    }

    def "should not generate cart when template not due today"() {
        given:
        def user = userRepository.save(TestDataFactory.buildUser())
        def group = groupRepository.save(TestDataFactory.buildGroup())
        def category = categoryRepository.save(TestDataFactory.buildCategory(group))
        def cart = cartRepository.save(TestDataFactory.buildCart(user, group, category, "Gym", 30, TestDataFactory.asDate(LocalDate.now().plusDays(1))))
        gmhRepository.save(TestDataFactory.buildGmh(user, group))

        def template = templateRepository.save(
                TestDataFactory.buildTemplate(cart, RecurrenceType.MONTHLY, LocalDate.now().plusDays(1))
        )

        when:
        cartService.generateRecurringCarts()

        then: "no cart created"
        cartRepository.findAll().toList().size() == 1

        and: "template unchanged except still active"
        def reloaded = templateRepository.findById(template.id).get()
        reloaded.nextExecutionDate == LocalDate.now().plusDays(1)
        reloaded.active
    }

    def "should not generate duplicate cart if similar exists"() {
        given:
        def user = userRepository.save(TestDataFactory.buildUser())
        def group = groupRepository.save(TestDataFactory.buildGroup())
        def category = categoryRepository.save(TestDataFactory.buildCategory(group))
        def cart = cartRepository.save(TestDataFactory.buildCart(user, group, category, "Netflix", 12.99, TestDataFactory.asDate(LocalDate.now())))
        gmhRepository.save(TestDataFactory.buildGmh(user, group))

        def template = templateRepository.save(
                TestDataFactory.buildTemplate(cart, RecurrenceType.MONTHLY, LocalDate.now())
        )

        when:
        cartService.generateRecurringCarts()

        then: "no additional cart created"
        def carts = cartRepository.findAll()
        carts.size() == 1

        and: "template moved to next execution date"
        def reloaded = templateRepository.findById(template.id).get()
        reloaded.nextExecutionDate == LocalDate.now().plusMonths(1)
    }

    def "should generate new cart when template due and no similar exists"() {
        given:
        def user = userRepository.save(TestDataFactory.buildUser())
        def group = groupRepository.save(TestDataFactory.buildGroup())
        def category = categoryRepository.save(TestDataFactory.buildCategory(group))
        def cart = cartRepository.save(TestDataFactory.buildCart(user, group, category, "Spotify", 9.99, TestDataFactory.asDate(LocalDate.now().plusDays(-1))))
        gmhRepository.save(TestDataFactory.buildGmh(user, group))

        def template = templateRepository.save(
                TestDataFactory.buildTemplate(cart, RecurrenceType.MONTHLY, LocalDate.now())
        )

        when:
        cartService.generateRecurringCarts()

        then: "a new cart is created"
        def carts = cartRepository.findAll()
        carts.size() == 2
        carts.forEach(c -> {
            c.title == "Spotify"
            c.amount == 9.99
            c.category.id == category.id
            c.user.id == user.id
            c.group.id == group.id
        })


        and: "template moved to next execution date"
        def reloaded = templateRepository.findById(template.id).get()
        reloaded.nextExecutionDate == LocalDate.now().plusMonths(1)
    }

    def "generateRecurringCarts should create cart and update nextExecutionDate for all recurrence types"() {
        given: "Persisted user, group, category and a recurring cart template"
        def user = userRepository.save(TestDataFactory.buildUser())
        def group = groupRepository.save(TestDataFactory.buildGroup())
        def category = categoryRepository.save(TestDataFactory.buildCategory(group))
        gmhRepository.save(TestDataFactory.buildGmh(user, group))
        def cart = cartRepository.save(
                TestDataFactory.buildCart(user, group, category, "Gym", 29.99, TestDataFactory.asDate(LocalDate.now().plusDays(-1)))
        )

        // Template with given recurrence type and nextExecutionDate = today
        def template = templateRepository.save(
                TestDataFactory.buildTemplate(cart, recurrenceType, LocalDate.now())
        )

        when: "Recurring carts are generated"
        cartService.generateRecurringCarts()

        then: "A new cart has been created"
        def carts = cartRepository.findAll()
        carts.size() == 2  // original + new recurring

        and: "Template has moved to the correct next execution date"
        def reloaded = templateRepository.findById(template.id).get()
        reloaded.nextExecutionDate == expectedNextDate

        where:
        recurrenceType         || expectedNextDate
        RecurrenceType.DAILY   || LocalDate.now().plusDays(1)
        RecurrenceType.WEEKLY  || LocalDate.now().plusWeeks(1)
        RecurrenceType.MONTHLY || LocalDate.now().plusMonths(1)
        RecurrenceType.YEARLY  || LocalDate.now().plusYears(1)
    }

}
