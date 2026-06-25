package com.github.svenfran.budgetapp.budgetappbackend.service.unit

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto
import com.github.svenfran.budgetapp.budgetappbackend.entity.*
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.service.TestDataFactory
import com.github.svenfran.budgetapp.budgetappbackend.service.VerificationService
import org.springframework.security.crypto.password.PasswordEncoder
import spock.lang.Specification

import java.time.LocalDate

class VerificationServiceSpec extends Specification {

    def userRepository = Mock(UserRepository)
    def passwordEncoder = Mock(PasswordEncoder)
    def service = new VerificationService(passwordEncoder, userRepository)

    def "verifyIsAuthenticatedUser throws when users differ"() {
        given:
        def user = TestDataFactory.buildUser("User")
        def authUser = TestDataFactory.buildUser("AuthUser")

        when:
        service.verifyIsAuthenticatedUser(user, authUser)

        then:
        thrown(UserIsNotAuthenticatedUser)
    }

    def "verifyIsAuthenticatedUser passes for same user"() {
        given:
        def user = TestDataFactory.buildUser("User")

        when:
        service.verifyIsAuthenticatedUser(user, user)

        then:
        noExceptionThrown()
    }

    def "verifyIsCorrectPassword throws on mismatch"() {
        given:
        def user = new User(password: "encoded")
        passwordEncoder.matches("wrong", "encoded") >> false

        when:
        service.verifyIsCorrectPassword(user, "wrong")

        then:
        thrown(WrongPasswordException)
    }

    def "verifyIsCorrectPassword passes on correct password"() {
        given:
        def user = new User(password: "encoded")
        passwordEncoder.matches("secret", "encoded") >> true

        when:
        service.verifyIsCorrectPassword(user, "secret")

        then:
        noExceptionThrown()
    }

    def "verifyEmailNotExists throws when email already exists"() {
        given:
        userRepository.findByEmail("test@mail.com") >> Optional.of(new User())

        when:
        service.verifyEmailNotExists("test@mail.com")

        then:
        thrown(UserAlreadyExistException)
    }

    def "verifyEmailNotExists passes when email not exists"() {
        given:
        userRepository.findByEmail("test@mail.com") >> Optional.empty()

        when:
        service.verifyEmailNotExists("test@mail.com")

        then:
        noExceptionThrown()
    }

    def "verifyUserNameNotExists throws when username exists"() {
        given:
        userRepository.findByName("john") >> Optional.of(new User())

        when:
        service.verifyUserNameNotExists("john")

        then:
        thrown(UserNameAlreadyExistsException)
    }

    def "verifyUserNameIsAllowed throws when name contains forbidden term #userName"() {
        when:
        service.verifyUserNameIsAllowed(userName)

        then:
        thrown(exception)

        where:
        userName            | exception
        "test_REMOVED_user" | UserNameNotAllowedException
        "test_DELETED_user" | UserNameNotAllowedException
    }

    def "verifyIsPartOfGroup throws when user not owner or member"() {
        given:
        def user = TestDataFactory.buildUser()
        def notOwner = TestDataFactory.buildUser("NotOwner")
        def group = new Group(owner: notOwner, members: [])

        when:
        service.verifyIsPartOfGroup(user, group)

        then:
        thrown(NotOwnerOrMemberOfGroupException)
    }

    def "verifyIsOwnerOfCart throws when user not owner"() {
        given:
        def user = TestDataFactory.buildUser()
        def notOwner = TestDataFactory.buildUser("NotOwner")
        def cart = new Cart(user: notOwner)

        when:
        service.verifyIsOwnerOfCart(user, cart)

        then:
        thrown(NotOwnerOfCartException)
    }

    def "verifyCategoryIsPartOfGroup throws when group IDs differ"() {
        given:
        def group = new Group(id: 1)
        def category = new Category(id: 2, group: new Group(id: 3))

        when:
        service.verifyCategoryIsPartOfGroup(category, group)

        then:
        thrown(CategoryBelongsNotToGroupException)
    }

    def "verifyCategoryNotInUse throws when category has carts"() {
        given:
        def category = new Category(id: 1, carts: [new Cart()])

        when:
        service.verifyCategoryNotInUse(category)

        then:
        thrown(CategoryIsUsedByCartException)
    }

    def "verifyIsGroupOwner throws when user is not owner"() {
        given:
        def user = new User(id: 1)
        def group = new Group(owner: new User(id: 2))

        when:
        service.verifyIsGroupOwner(user, group)

        then:
        thrown(NotOwnerOfGroupException)
    }

    def "verifyUserExists throws when user is null"() {
        when:
        service.verifyUserExists(null)

        then:
        thrown(UserNotFoundException)
    }

    def "verifyCurrentlyNoGroupMember throws when user already in group"() {
        given:
        def user = new User(id: 1)
        def group = new Group(members: [user])

        when:
        service.verifyCurrentlyNoGroupMember(user, group)

        then:
        thrown(MemberAlreadyExixtsException)
    }

    def "verifyMemberNotGroupOwner throws when member equals owner"() {
        given:
        def user = new User(id: 1)

        when:
        service.verifyMemberNotGroupOwner(user, user)

        then:
        thrown(MemberEqualsOwnerException)
    }

    def "verifyIsOwnerOrMemberToRemove throws when neither owner nor removed member"() {
        given:
        def user = new User(id: 1)
        def removed = new User(id: 2)
        def group = new Group(owner: new User(id: 3))

        when:
        service.verifyIsOwnerOrMemberToRemove(user, removed, group)

        then:
        thrown(NotOwnerOfGroupException)
    }

    def "verifyShoppingListIsPartOfGroup throws when group mismatch"() {
        given:
        def group = new Group(id: 1)
        def shoppingList = new ShoppingList(id: 1, group: new Group(id: 2))

        when:
        service.verifyShoppingListIsPartOfGroup(shoppingList, group)

        then:
        thrown(ShoppingListDoesNotBelongToGroupException)
    }

    def "verifyShoppingItemIsPartOfShoppingList throws when not same list"() {
        given:
        def list = new ShoppingList(id: 1)
        def item = new ShoppingItem(id: 2, shoppingList: new ShoppingList(id: 3))

        when:
        service.verifyShoppingItemIsPartOfShoppingList(list, item)

        then:
        thrown(ShoppingItemDoesNotBelongToShoppingListException)
    }

    def "verifyAllShoppingItemsBelongToSameShoppingListAndGroup throws when inconsistent"() {
        given:
        def items = [
                new AddEditShoppingItemDto(groupId: 1, shoppingListId: 1),
                new AddEditShoppingItemDto(groupId: 2, shoppingListId: 1)
        ]

        when:
        service.verifyAllShoppingItemsBelongToSameShoppingListAndGroup(items)

        then:
        thrown(IllegalArgumentException)
    }

    def "verifyDatePurchasedWithinMembershipPeriod throws when outside period"() {
        given:
        def start = TestDataFactory.asDate(LocalDate.now().minusDays(10))
        def end = TestDataFactory.asDate(LocalDate.now().minusDays(5))
        def gmh = [new GroupMembershipHistory(membershipStart: start, membershipEnd: end)]
        def date = TestDataFactory.asDate(LocalDate.now())

        when:
        service.verifyDatePurchasedWithinMembershipPeriod(gmh, date)

        then:
        thrown(DatePurchasedNotWithinMembershipPeriodException)
    }

    def "verifyDatePurchasedWithinMembershipPeriod passes when within period"() {
        given:
        def start = TestDataFactory.asDate(LocalDate.now().minusDays(10))
        def end = TestDataFactory.asDate(LocalDate.now().plusDays(5))
        def gmh = [new GroupMembershipHistory(membershipStart: start, membershipEnd: end)]
        def date = TestDataFactory.asDate(LocalDate.now())

        when:
        service.verifyDatePurchasedWithinMembershipPeriod(gmh, date)

        then:
        noExceptionThrown()
    }
}
