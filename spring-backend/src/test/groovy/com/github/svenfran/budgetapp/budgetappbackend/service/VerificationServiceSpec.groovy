package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationRequest
import com.github.svenfran.budgetapp.budgetappbackend.dto.SettlementPaymentDto
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

import java.time.LocalDate

class VerificationServiceSpec extends Specification {

    static final LocalDate BEGINN = LocalDate.of(2025, 6, 5)
    static final LocalDate ENDE = LocalDate.of(2025, 6, 10)

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

    def "Kaufdatum #fall wird #ergebnis"() {
        given: 'Mitgliedschaft vom 05.06. bis 10.06.2025, bei mehreren Zeiträumen zusätzlich ab 20.06.2025 offen'
        def zeitraum = { LocalDate von, LocalDate bis ->
            TestData.membership(membershipStart: DateUtils.toDate(von), membershipEnd: bis ? DateUtils.toDate(bis) : null)
        }
        def gmh = offenesEnde
                ? [zeitraum(BEGINN, null)]
                : [zeitraum(BEGINN, ENDE)] + (zweiterZeitraum ? [zeitraum(LocalDate.of(2025, 6, 20), null)] : [])

        when:
        Exception fehler = null
        try {
            service.verifyDatePurchasedWithinMembershipPeriod(gmh, kaufdatum)
        } catch (DatePurchasedNotWithinMembershipPeriodException e) {
            fehler = e
        }

        then:
        (fehler == null) == erlaubt
        fehler == null || fehler.message == 'Date purchased is not within membership period'

        where:
        fall                                    | kaufdatum                 | offenesEnde | zweiterZeitraum || erlaubt
        'am Beginntag'                          | BEGINN                    | false       | false           || true
        'am Endtag'                             | ENDE                      | false       | false           || true
        'innerhalb der Mitgliedschaft'          | LocalDate.of(2025, 6, 7)  | false       | false           || true
        'am Tag vor dem Beginn'                 | BEGINN.minusDays(1)       | false       | false           || false
        'am Tag nach dem Ende'                  | ENDE.plusDays(1)          | false       | false           || false
        'lange nach Beginn bei offenem Ende'    | LocalDate.of(2030, 1, 1)  | true        | false           || true
        'in der Lücke zwischen zwei Zeiträumen' | LocalDate.of(2025, 6, 15) | false       | true            || false
        'im zweiten Zeitraum'                   | LocalDate.of(2025, 6, 20) | false       | true            || true

        ergebnis = erlaubt ? 'akzeptiert' : 'abgelehnt'
    }

    def "Fehlendes Kaufdatum wird mit eigener Meldung abgelehnt"() {
        given:
        def gmh = [TestData.membership(membershipStart: DateUtils.toDate(BEGINN), membershipEnd: null)]

        when:
        service.verifyDatePurchasedWithinMembershipPeriod(gmh, null)

        then:
        def fehler = thrown(DatePurchasedNotWithinMembershipPeriodException)
        fehler.message == 'Date purchased is required'
    }

    // ----- Ausgleichszahlung (DIV-74) -----

    def "Gültige Ausgleichszahlung (Betrag #betrag) wird akzeptiert"() {
        given:
        def dto = new SettlementPaymentDto(betrag, 1L, new UserDto(id: 2L), TestData.REFERENCE_DATE)

        when:
        service.verifyValidSettlementPaymentForCreate(dto)

        then:
        noExceptionThrown()

        where:
        betrag << [0.01d, 1d, 30.1d, 30.10d, 1234.56d]
    }

    def "Betrag #betrag einer Ausgleichszahlung wird abgelehnt"() {
        when:
        service.verifyValidSettlementAmount(betrag)

        then:
        def e = thrown(InvalidSettlementPaymentException)
        e.message.contains(meldung)

        where:
        betrag                   || meldung
        null                     || "'amount'"
        0d                       || 'greater than 0'
        -0.01d                   || 'greater than 0'
        1.234d                   || 'at most 2 decimal'
        0.001d                   || 'at most 2 decimal'
        Double.NaN               || 'greater than 0'
        Double.POSITIVE_INFINITY || 'greater than 0'
    }

    def "Fehlendes Pflichtfeld #feld einer Ausgleichszahlung wird vor jedem Laden abgelehnt"() {
        given:
        def dto = new SettlementPaymentDto(30d, 1L, new UserDto(id: 2L), TestData.REFERENCE_DATE)
        aendern(dto)

        when:
        service.verifyValidSettlementPaymentForCreate(dto)

        then:
        def e = thrown(InvalidSettlementPaymentException)
        e.message == "Settlement payment field '$feld' is required".toString()

        where:
        feld            | aendern
        'groupId'       | { SettlementPaymentDto d -> d.groupId = null }
        'member'        | { SettlementPaymentDto d -> d.member = null }
        'member.id'     | { SettlementPaymentDto d -> d.member.id = null }
        'datePurchased' | { SettlementPaymentDto d -> d.datePurchased = null }
        'amount'        | { SettlementPaymentDto d -> d.amount = null }
    }

    def "Anlegen mit Zahler ohne Id wird vor jedem Laden abgelehnt, ohne Zahler und mit Zahler-Id akzeptiert (Phase 5)"() {
        given:
        def dto = new SettlementPaymentDto(30d, 1L, new UserDto(id: 2L), TestData.REFERENCE_DATE)
        dto.payer = payer

        when:
        def meldung = null
        try {
            service.verifyValidSettlementPaymentForCreate(dto)
        } catch (InvalidSettlementPaymentException e) {
            meldung = e.message
        }

        then:
        meldung == erwartet

        where:
        payer                || erwartet
        null                 || null
        new UserDto(id: 3L)  || null
        new UserDto()        || "Settlement payment field 'payer.id' is required"
    }

    def "Anlegen durch #rolle #ergebnis (Zahler 1, Empfänger 2)"() {
        when:
        def abgelehnt = false
        try {
            service.verifyIsPartyOfPayment(TestData.user(id: userId), TestData.user(id: 1L), TestData.user(id: 2L))
        } catch (NotPartyOfSettlementException ignored) {
            abgelehnt = true
        }

        then:
        abgelehnt == erwartetAbgelehnt

        where:
        rolle       | userId || erwartetAbgelehnt
        'Zahler'    | 1L     || false
        'Empfänger' | 2L     || false
        'Dritten'   | 3L     || true
        ergebnis = erwartetAbgelehnt ? 'wird abgelehnt' : 'ist erlaubt'
    }

    def "Ausgleichszahlung ohne Anfrage wird abgelehnt"() {
        when:
        service.verifyValidSettlementPaymentForCreate(null)

        then:
        thrown(InvalidSettlementPaymentException)
    }

    def "Zahler und Empfänger #fall"() {
        given:
        def anna = TestData.user(id: 1L)
        def empfaenger = TestData.user(id: empfaengerId)

        when:
        def abgelehnt = false
        try {
            service.verifyPayerIsNotReceiver(anna, empfaenger)
        } catch (InvalidSettlementPaymentException ignored) {
            abgelehnt = true
        }

        then:
        abgelehnt == erwartetAbgelehnt

        where:
        fall                                    | empfaengerId || erwartetAbgelehnt
        'sind dieselbe Person: abgelehnt'       | 1L           || true
        'sind verschiedene Personen: akzeptiert' | 2L          || false
    }

    def "Bearbeiten: fehlendes Pflichtfeld #feld wird vor jedem Laden abgelehnt, groupId ist nicht nötig"() {
        given:
        def dto = new SettlementPaymentDto(30d, null, new UserDto(id: 2L), TestData.REFERENCE_DATE)
        dto.id = 70L
        aendern(dto)

        when:
        def abgelehnt = null
        try {
            service.verifyValidSettlementPaymentForUpdate(dto)
        } catch (InvalidSettlementPaymentException e) {
            abgelehnt = e.message
        }

        then:
        abgelehnt == (feld ? "Settlement payment field '$feld' is required".toString() : null)

        where:
        feld            | aendern
        null            | { SettlementPaymentDto d -> }
        'id'            | { SettlementPaymentDto d -> d.id = null }
        'member'        | { SettlementPaymentDto d -> d.member = null }
        'member.id'     | { SettlementPaymentDto d -> d.member.id = null }
        'datePurchased' | { SettlementPaymentDto d -> d.datePurchased = null }
        'amount'        | { SettlementPaymentDto d -> d.amount = null }
    }

    def "Empfänger ändern: #fall"() {
        given:
        def zahler = TestData.user(id: 1L)
        def empfaenger = TestData.user(id: 2L)
        def settlement = TestData.settlement(id: 70L, payer: zahler, receiver: empfaenger)
        def handelnder = TestData.user(id: handelnderId)

        when:
        def abgelehnt = false
        try {
            service.verifyMayChangeReceiver(handelnder, settlement, TestData.user(id: neuerEmpfaengerId))
        } catch (ReceiverChangeNotAllowedException ignored) {
            abgelehnt = true
        }

        then:
        abgelehnt == erwartetAbgelehnt

        where:
        fall                                          | handelnderId | neuerEmpfaengerId || erwartetAbgelehnt
        'Zahler wechselt den Empfänger: erlaubt'      | 1L           | 3L                || false
        'Empfänger wechselt den Empfänger: abgelehnt' | 2L           | 3L                || true
        'Empfänger behält sich als Empfänger: erlaubt' | 2L          | 2L                || false
    }

    def "Einkauf mit Ausgleichszahlung ist über normale Endpunkte gesperrt, ohne erlaubt"() {
        when:
        service.verifyCartIsNotSettlement(TestData.cart(id: 5L, settlement: TestData.settlement(id: 70L)))

        then:
        def e = thrown(SettlementCartNotEditableException)
        e.message.contains('Cart with Id 5')

        when:
        service.verifyCartIsNotSettlement(TestData.cart(id: 6L))

        then:
        noExceptionThrown()
    }

    def "Settlement-Kategorie ist für normale Einkäufe gesperrt, andere Kategorien nicht"() {
        when:
        service.verifyCategoryIsNotSettlement(TestData.category(id: 20L, settlement: true))

        then:
        thrown(SettlementCategoryNotAllowedException)

        when:
        service.verifyCategoryIsNotSettlement(TestData.category(id: 21L))

        then:
        noExceptionThrown()
    }

    def "#rolle #ergebnis Ausgleichszahlung ändern oder löschen"() {
        given:
        def settlement = TestData.settlement(id: 70L, payer: TestData.user(id: 1L), receiver: TestData.user(id: 2L))
        def user = TestData.user(id: userId)

        when:
        def abgelehnt = false
        try {
            service.verifyIsPartyOfSettlement(user, settlement)
        } catch (NotPartyOfSettlementException ignored) {
            abgelehnt = true
        }

        then:
        abgelehnt == erwartetAbgelehnt

        where:
        rolle        | userId || erwartetAbgelehnt
        'Zahler'     | 1L     || false
        'Empfänger'  | 2L     || false
        'Dritter'    | 3L     || true
        ergebnis = erwartetAbgelehnt ? 'darf keine' : 'darf die'
    }

    def "Währungswechsel: Buchungen #hasBookings, Bestätigung #confirmed -> abgelehnt #erwartetAbgelehnt"() {
        when: "nur bei tatsächlichem Wechsel aufgerufen (DIV-77)"
        def abgelehnt = false
        try {
            service.verifyCurrencyChangeConfirmed(hasBookings, confirmed)
        } catch (CurrencyChangeConfirmationRequiredException ignored) {
            abgelehnt = true
        }

        then:
        abgelehnt == erwartetAbgelehnt

        where:
        hasBookings | confirmed || erwartetAbgelehnt
        false       | null      || false
        false       | false     || false
        false       | true      || false
        true        | null      || true
        true        | false     || true
        true        | true      || false
    }

    def "Cart-Betrag #betrag: abgelehnt #erwartetAbgelehnt (DIV-77)"() {
        when:
        def abgelehnt = false
        try {
            service.verifyCartAmountHasAtMostTwoDecimals(betrag)
        } catch (InvalidAmountException ignored) {
            abgelehnt = true
        }

        then:
        abgelehnt == erwartetAbgelehnt

        where:
        betrag               || erwartetAbgelehnt
        12d                  || false
        12.5d                || false
        12.50d               || false
        -3.99d               || false
        null                 || false
        12.345d              || true
        0.30000000000000004d || true
    }

    /** BindingResult wie nach @Valid, mit genau einem Feldfehler (Fehlercode beliebig). */
    private static BindingResult bindingResultMitFehlerAn(Object dto, String feld) {
        def bindingResult = new BeanPropertyBindingResult(dto, "request")
        bindingResult.rejectValue(feld, "invalid")
        bindingResult
    }
}
