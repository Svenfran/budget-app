package com.github.svenfran.budgetapp.budgetappbackend.service.integration

import com.github.svenfran.budgetapp.budgetappbackend.BudgetAppApplication
import com.github.svenfran.budgetapp.budgetappbackend.constants.RecurrenceType
import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto
import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart
import com.github.svenfran.budgetapp.budgetappbackend.entity.CartTemplate
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartTemplateRepository
import com.github.svenfran.budgetapp.budgetappbackend.service.CartService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate

@Transactional
@SpringBootTest(classes = BudgetAppApplication, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CartServiceIntegrationSpec extends Specification {

    @Autowired
    CartService cartService

    @Autowired
    CartRepository cartRepository

    @Autowired
    CartTemplateRepository templateRepository

    @Unroll
    def "updateCart transitions from #oldType to #newType with updateSelected=#updateSelected"() {
        given: "a cart with an optional template"
        def oldTemplate = oldType != RecurrenceType.NONE ?
                templateRepository.save(new CartTemplate(
                        userId: 1L,
                        groupId: 1L,
                        categoryId: 1L,
                        title: "Template",
                        amount: 100.0,
                        recurrenceType: oldType,
                        active: true,
                        startDate: LocalDate.of(2024,1,1)
                )) : null

        def cart = cartRepository.save(new Cart(
                title: "TestCart",
                amount: 100.0,
                template: oldTemplate
        ))

        def dto = new CartDto(
                id: cart.id,
                title: "TestCart Updated",
                amount: 200.0,
                recurrenceType: newType,
                templateUpdateSelected: updateSelected,
                datePurchased: new Date()
        )

        when: "updateCart is called"
        cartService.updateCart(dto)

        then: "the template state is as expected"
        def updatedCart = cartRepository.findById(cart.id).get()
        def allTemplates = templateRepository.findAll()

        if (expectedActiveTemplates == 0) {
            assert updatedCart.template == null
            assert allTemplates.every { !it.active }
        } else {
            assert updatedCart.template != null
            assert allTemplates.count { it.active } == expectedActiveTemplates
            assert updatedCart.template.recurrenceType == expectedRecurrence
        }

        where:
        oldType                | newType                | updateSelected || expectedActiveTemplates | expectedRecurrence
        RecurrenceType.MONTHLY | RecurrenceType.MONTHLY | false          || 1                       | RecurrenceType.MONTHLY
        RecurrenceType.MONTHLY | RecurrenceType.MONTHLY | true           || 1                       | RecurrenceType.MONTHLY
        RecurrenceType.MONTHLY | RecurrenceType.WEEKLY  | false          || 1                       | RecurrenceType.WEEKLY
        RecurrenceType.MONTHLY | RecurrenceType.NONE    | false          || 0                       | null
        RecurrenceType.NONE    | RecurrenceType.DAILY   | false          || 1                       | RecurrenceType.DAILY
        RecurrenceType.NONE    | RecurrenceType.NONE    | false          || 0                       | null
    }
}
