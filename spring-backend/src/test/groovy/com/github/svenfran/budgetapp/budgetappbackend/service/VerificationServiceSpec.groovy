package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationRequest
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto
import com.github.svenfran.budgetapp.budgetappbackend.entity.*
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.BindingResult
import spock.lang.Specification

class VerificationServiceSpec extends Specification {

    def userRepository = Mock(UserRepository)
    def passwordEncoder = Mock(PasswordEncoder)
    def service = new VerificationService(passwordEncoder, userRepository)

    def "Anderer Nutzer gilt nicht als angemeldeter Nutzer"() {
        given:
        def user = TestData.user(name: "User")
        def authUser = TestData.user(name: "AuthUser")

        when:
        service.verifyIsAuthenticatedUser(user, authUser)

        then:
        thrown(UserIsNotAuthenticatedUser)
    }

    def "Derselbe Nutzer gilt als angemeldeter Nutzer"() {
        given:
        def user = TestData.user(name: "User")

        when:
        service.verifyIsAuthenticatedUser(user, user)

        then:
        noExceptionThrown()
    }

    def "Falsches Passwort wird abgelehnt"() {
        given:
        def user = new User(password: "encoded")
        passwordEncoder.matches("wrong", "encoded") >> false

        when:
        service.verifyIsCorrectPassword(user, "wrong")

        then:
        thrown(WrongPasswordException)
    }

    def "Richtiges Passwort wird akzeptiert"() {
        given:
        def user = new User(password: "encoded")
        passwordEncoder.matches("secret", "encoded") >> true

        when:
        service.verifyIsCorrectPassword(user, "secret")

        then:
        noExceptionThrown()
    }

    def "Bereits vergebene E-Mail wird abgelehnt"() {
        given:
        userRepository.findByEmail("test@mail.com") >> Optional.of(new User())

        when:
        service.verifyEmailNotExists("test@mail.com")

        then:
        thrown(UserAlreadyExistException)
    }

    def "Noch freie E-Mail wird akzeptiert"() {
        given:
        userRepository.findByEmail("test@mail.com") >> Optional.empty()

        when:
        service.verifyEmailNotExists("test@mail.com")

        then:
        noExceptionThrown()
    }

    def "Validierungsfehler am E-Mail-Feld #feld wird als ungültige E-Mail abgelehnt"() {
        given:
        def bindingResult = bindingResultMitFehlerAn(dto, feld)

        when:
        service.verifyEmailIsValid(bindingResult, feld)

        then:
        thrown(InvalidEmailException)

        where:
        feld        | dto
        "email"     | new AuthenticationRequest()
        "userEmail" | new UserDto()
    }

    def "Validierungsfehler nur an der DeviceId gilt nicht als ungültige E-Mail"() {
        given:
        def bindingResult = bindingResultMitFehlerAn(new AuthenticationRequest(), "deviceId")

        when:
        service.verifyEmailIsValid(bindingResult, "email")

        then:
        noExceptionThrown()
    }

    def "Fehlende DeviceId wird abgelehnt"() {
        given:
        def bindingResult = bindingResultMitFehlerAn(new AuthenticationRequest(), "deviceId")

        when:
        service.verifyDeviceIdIsPresent(bindingResult)

        then:
        thrown(MissingDeviceIdException)
    }

    def "Validierungsfehler nur an der E-Mail gilt nicht als fehlende DeviceId"() {
        given:
        def bindingResult = bindingResultMitFehlerAn(new AuthenticationRequest(), "email")

        when:
        service.verifyDeviceIdIsPresent(bindingResult)

        then:
        noExceptionThrown()
    }

    def "Bereits vergebener Nutzername wird abgelehnt"() {
        given:
        userRepository.findByName("john") >> Optional.of(new User())

        when:
        service.verifyUserNameNotExists("john")

        then:
        thrown(UserNameAlreadyExistsException)
    }

    def "Nutzername #userName mit reserviertem Begriff wird abgelehnt"() {
        when:
        service.verifyUserNameIsAllowed(userName)

        then:
        thrown(exception)

        where:
        userName            | exception
        "test_REMOVED_user" | UserNameNotAllowedException
        "test_DELETED_user" | UserNameNotAllowedException
    }

    def "Nutzer, der weder Owner noch Mitglied ist, gehört nicht zur Gruppe"() {
        given:
        def user = TestData.user()
        def notOwner = TestData.user(name: "NotOwner")
        def group = TestData.group(owner: notOwner)

        when:
        service.verifyIsPartOfGroup(user, group)

        then:
        thrown(NotOwnerOrMemberOfGroupException)
    }

    def "Owner gehört zur Gruppe"() {
        given:
        def user = TestData.user()
        def group = TestData.group(owner: user)

        when:
        service.verifyIsPartOfGroup(user, group)

        then:
        noExceptionThrown()
    }

    def "Mitglied gehört zur Gruppe"() {
        given:
        def user = TestData.user()
        def group = TestData.group(owner: TestData.user(), members: [user] as Set)

        when:
        service.verifyIsPartOfGroup(user, group)

        then:
        noExceptionThrown()
    }

    def "Anderer Nutzer ist nicht Eigentümer des Einkaufs"() {
        given:
        def user = TestData.user()
        def notOwner = TestData.user(name: "NotOwner")
        def cart = new Cart(user: notOwner)

        when:
        service.verifyIsOwnerOfCart(user, cart)

        then:
        thrown(NotOwnerOfCartException)
    }

    def "Kategorie einer anderen Gruppe wird abgelehnt"() {
        given:
        def group = new Group(id: 1)
        def category = new Category(id: 2, group: new Group(id: 3))

        when:
        service.verifyCategoryIsPartOfGroup(category, group)

        then:
        thrown(CategoryBelongsNotToGroupException)
    }

    def "Kategorie mit Einkäufen gilt als in Benutzung"() {
        given:
        def category = new Category(id: 1, carts: [new Cart()])

        when:
        service.verifyCategoryNotInUse(category)

        then:
        thrown(CategoryIsUsedByCartException)
    }

    def "Nutzer, der nicht Owner ist, wird als Gruppen-Owner abgelehnt"() {
        given:
        def user = new User(id: 1)
        def group = new Group(owner: new User(id: 2))

        when:
        service.verifyIsGroupOwner(user, group)

        then:
        thrown(NotOwnerOfGroupException)
    }

    def "Fehlender Nutzer wird als nicht gefunden gemeldet"() {
        when:
        service.verifyUserExists(null)

        then:
        thrown(UserNotFoundException)
    }

    def "Bestehendes Mitglied kann nicht erneut hinzugefügt werden"() {
        given:
        def user = new User(id: 1)
        def group = new Group(members: [user])

        when:
        service.verifyCurrentlyNoGroupMember(user, group)

        then:
        thrown(MemberAlreadyExistsException)
    }

    def "Owner kann nicht als Mitglied hinzugefügt werden"() {
        given:
        def user = new User(id: 1)

        when:
        service.verifyMemberNotGroupOwner(user, user)

        then:
        thrown(MemberEqualsOwnerException)
    }

    def "Unbeteiligter Nutzer darf kein Mitglied aus der Gruppe entfernen"() {
        given:
        def user = new User(id: 1)
        def removed = new User(id: 2)
        def group = new Group(owner: new User(id: 3))

        when:
        service.verifyIsOwnerOrMemberToRemove(user, removed, group)

        then:
        thrown(NotOwnerOfGroupException)
    }

    def "Mitglied darf sich selbst aus der Gruppe entfernen"() {
        given:
        def member = TestData.user()
        def group = TestData.group(owner: TestData.user(), members: [member] as Set)

        when:
        service.verifyIsOwnerOrMemberToRemove(member, member, group)

        then:
        noExceptionThrown()
    }

    def "Einkaufsliste einer anderen Gruppe wird abgelehnt"() {
        given:
        def group = new Group(id: 1)
        def shoppingList = new ShoppingList(id: 1, group: new Group(id: 2))

        when:
        service.verifyShoppingListIsPartOfGroup(shoppingList, group)

        then:
        thrown(ShoppingListDoesNotBelongToGroupException)
    }

    def "Artikel einer anderen Einkaufsliste wird abgelehnt"() {
        given:
        def list = new ShoppingList(id: 1)
        def item = new ShoppingItem(id: 2, shoppingList: new ShoppingList(id: 3))

        when:
        service.verifyShoppingItemIsPartOfShoppingList(list, item)

        then:
        thrown(ShoppingItemDoesNotBelongToShoppingListException)
    }

    def "Artikel aus verschiedenen Gruppen werden gemeinsam abgelehnt"() {
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

    def "Kaufdatum außerhalb der Mitgliedschaft wird abgelehnt"() {
        given:
        def start = DateUtils.toDate(TestData.REFERENCE_DATE.minusDays(10))
        def end = DateUtils.toDate(TestData.REFERENCE_DATE.minusDays(5))
        def gmh = [TestData.membership(membershipStart: start, membershipEnd: end)]
        def date = DateUtils.toDate(TestData.REFERENCE_DATE)

        when:
        service.verifyDatePurchasedWithinMembershipPeriod(gmh, date)

        then:
        thrown(DatePurchasedNotWithinMembershipPeriodException)
    }

    def "Kaufdatum innerhalb der Mitgliedschaft wird akzeptiert"() {
        given:
        def start = DateUtils.toDate(TestData.REFERENCE_DATE.minusDays(10))
        def end = DateUtils.toDate(TestData.REFERENCE_DATE.plusDays(5))
        def gmh = [TestData.membership(membershipStart: start, membershipEnd: end)]
        def date = DateUtils.toDate(TestData.REFERENCE_DATE)

        when:
        service.verifyDatePurchasedWithinMembershipPeriod(gmh, date)

        then:
        noExceptionThrown()
    }

    /** BindingResult wie nach @Valid, mit genau einem Feldfehler (Fehlercode beliebig). */
    private static BindingResult bindingResultMitFehlerAn(Object dto, String feld) {
        def bindingResult = new BeanPropertyBindingResult(dto, "request")
        bindingResult.rejectValue(feld, "invalid")
        bindingResult
    }
}
