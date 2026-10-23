package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.constants.UserEnum
import com.github.svenfran.budgetapp.budgetappbackend.entity.Cart
import com.github.svenfran.budgetapp.budgetappbackend.entity.Group
import com.github.svenfran.budgetapp.budgetappbackend.entity.GroupMembershipHistory
import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.springframework.security.crypto.password.PasswordEncoder
import spock.lang.Specification

import java.time.Duration
import java.time.LocalDate

import static java.time.LocalDate.of

/**
 * Öffentliche Hilfsmethoden von SpendingsOverviewService (ohne Spring). Mitgliedschaften als Liste von
 * [Beginn, Ende]-Paaren; Ende null = offen (NO_END_DATE).
 *
 * Die Übersichts-Features (DIV-34) laufen mit gemocktem DataLoaderService/CartRepository und prüfen Ladeaufrufe,
 * Nutzernamen und Nutzerreihenfolge; die Werte selbst prüft SpendingsOverviewServiceIntegrationSpec.
 */
class SpendingsOverviewServiceSpec extends Specification {

    static final Long GRUPPE_ID = 1L

    def dataLoaderService = Mock(DataLoaderService)
    def cartRepository = Mock(CartRepository)
    def service = new SpendingsOverviewService()

    def setup() {
        service.dataLoaderService = dataLoaderService
        service.cartRepository = cartRepository
        service.verificationService = new VerificationService(Mock(PasswordEncoder), Mock(UserRepository))
    }

    def "#endpoint lädt Carts und Mitgliedschaftshistorie genau einmal und jeden Nutzer höchstens einmal"() {
        given: "Anna (Owner) und Ben seit 2024; Einkäufe 2024 und in zwei Monaten 2025, also mehrere Perioden"
        def anna = TestData.user(id: 101L, name: 'Anna')
        def ben = TestData.user(id: 102L, name: 'Ben')
        def gruppe = gruppe(anna, [ben])
        def historie = [anna, ben].collect { mitgliedschaft(it, of(2024, 1, 1), null) }
        def carts = [einkauf(1L, gruppe, anna, of(2024, 5, 1)),
                     einkauf(2L, gruppe, ben, of(2025, 1, 10)),
                     einkauf(3L, gruppe, anna, of(2025, 3, 10))]
        angemeldet(anna, gruppe, [2025, 2024])

        when:
        aufruf(service)

        then:
        1 * dataLoaderService.loadCartListForGroup(GRUPPE_ID) >> carts
        1 * dataLoaderService.loadMembershipHistoryForGroup(GRUPPE_ID) >> historie
        0 * dataLoaderService.loadMembershipHistoryForGroupAndUser(*_)
        1 * dataLoaderService.loadUser(101L) >> anna
        1 * dataLoaderService.loadUser(102L) >> ben

        where:
        endpoint               | aufruf
        'Gesamtübersicht'      | { SpendingsOverviewService s -> s.getSpendingsForGroupAndAllYears(GRUPPE_ID) }
        'Jahresübersicht 2025' | { SpendingsOverviewService s -> s.getSpendingsForGroupAndYear(2025, GRUPPE_ID) }
    }

    def "Gelöschter Nutzer ohne offene Mitgliedschaft erscheint ohne REMOVED-Zusatz"() {
        given: "Anna (Owner) offen; gelöschter Nutzer und Ben nur 2024 Mitglied"
        def anna = TestData.user(id: 101L, name: 'Anna')
        def ben = TestData.user(id: 102L, name: 'Ben')
        def geloescht = TestData.user(id: 103L, name: UserEnum.USER_DELETED.getName())
        def gruppe = gruppe(anna, [])
        def historie = [mitgliedschaft(anna, of(2024, 1, 1), null),
                        mitgliedschaft(ben, of(2024, 1, 1), of(2024, 12, 31)),
                        mitgliedschaft(geloescht, of(2024, 1, 1), of(2024, 12, 31))]
        angemeldet(anna, gruppe, [2024])
        stammdaten([anna, ben, geloescht], historie, [einkauf(1L, gruppe, anna, of(2024, 5, 1))])

        when:
        def gesamt = service.getSpendingsForGroupAndAllYears(GRUPPE_ID)

        then:
        gesamt.spendingsTotalYear.spendingsTotalUser.collectEntries { [it.userId, it.userName] } ==
                [(101L): 'Anna', (102L): 'Ben REMOVED', (103L): 'DELETED']
    }

    def "Nutzer erscheinen in der Reihenfolge der Mitgliedschaftshistorie, auch bei mehreren Mitgliedschaften"() {
        given: "Historie Ben, Anna, Ben (Wiedereintritt) – nicht nach ID sortiert"
        def anna = TestData.user(id: 101L, name: 'Anna')
        def ben = TestData.user(id: 102L, name: 'Ben')
        def gruppe = gruppe(anna, [ben])
        def historie = [mitgliedschaft(ben, of(2024, 1, 1), of(2024, 6, 30)),
                        mitgliedschaft(anna, of(2024, 1, 1), null),
                        mitgliedschaft(ben, of(2024, 9, 1), null)]
        angemeldet(anna, gruppe, [2024])
        stammdaten([anna, ben], historie, [einkauf(1L, gruppe, anna, of(2024, 10, 1))])

        when:
        def gesamt = service.getSpendingsForGroupAndAllYears(GRUPPE_ID)
        def jahr = service.getSpendingsForGroupAndYear(2024, GRUPPE_ID)

        then:
        gesamt.spendingsTotalYear.spendingsTotalUser*.userId == [102L, 101L]
        gesamt.spendingsPerYear.collect { it.spendingsYearlyUser*.userId } == [[102L, 101L]]
        jahr.spendingsTotalYear.spendingsTotalUser*.userId == [102L, 101L]
        jahr.spendingsPerMonth.collect { it.spendingsMonthlyUser*.userId } == [[102L, 101L]]
    }

    def "Jahresübersicht für ein Jahr ohne Carts lädt keine Nutzer"() {
        given: "Einkauf nur 2024"
        def anna = TestData.user(id: 101L, name: 'Anna')
        def gruppe = gruppe(anna, [])
        def historie = [mitgliedschaft(anna, of(2024, 1, 1), null)]
        angemeldet(anna, gruppe, [2024])
        dataLoaderService.loadCartListForGroup(GRUPPE_ID) >> [einkauf(1L, gruppe, anna, of(2024, 5, 1))]
        dataLoaderService.loadMembershipHistoryForGroup(GRUPPE_ID) >> historie

        when:
        def jahr = service.getSpendingsForGroupAndYear(2025, GRUPPE_ID)

        then:
        0 * dataLoaderService.loadUser(_)
        jahr.spendingsTotalYear.spendingsTotalUser == []
        jahr.spendingsPerMonth == []
    }

    def "Mitgliedschaften #zeitraeume zählen im Jahr #year: #expected (#fall)"() {
        expect:
        service.wasUserMemberInYear(history(zeitraeume), year) == expected

        where:
        zeitraeume                                                    | year || expected | fall
        [[of(2024, 3, 1), null]]                                      | 2024 || true     | 'Eintritt im Jahr'
        [[of(2024, 3, 1), null]]                                      | 2023 || false    | 'vor Eintritt'
        [[of(2024, 3, 1), null]]                                      | 2030 || true     | 'offenes Ende'
        [[of(2023, 6, 1), of(2024, 12, 31)]]                          | 2024 || true     | 'Austritt an Silvester'
        [[of(2023, 6, 1), of(2024, 12, 31)]]                          | 2025 || false    | 'Jahreswechsel nach Austritt'
        [[of(2024, 12, 31), null]]                                    | 2024 || true     | 'Eintritt an Silvester'
        [[of(2023, 6, 1), of(2025, 1, 1)]]                            | 2025 || true     | 'ein Tag im Jahr reicht'
        [[of(2022, 1, 1), of(2022, 6, 30)], [of(2024, 2, 1), null]]   | 2023 || false    | 'Lücke zwischen zwei Mitgliedschaften'
        [[of(2022, 1, 1), of(2022, 6, 30)], [of(2024, 2, 1), null]]   | 2022 || true     | 'erste Mitgliedschaft greift'
        [[of(2022, 1, 1), of(2022, 6, 30)], [of(2024, 2, 1), null]]   | 2024 || true     | 'zweite Mitgliedschaft greift'
        []                                                            | 2025 || false    | 'keine Historie'
    }

    def "Mitgliedschaften #zeitraeume zählen im Monat #year-#month: #expected (#fall)"() {
        expect:
        service.wasUserMemberInMonth(history(zeitraeume), year, month) == expected

        where:
        zeitraeume                                                    | year | month || expected | fall
        [[of(2025, 3, 10), null]]                                     | 2025 | 3     || true     | 'Beginn mitten im Monat'
        [[of(2025, 3, 10), null]]                                     | 2025 | 2     || false    | 'vor Beginn'
        [[of(2024, 1, 1), of(2025, 1, 31)]]                           | 2025 | 1     || true     | 'Ende am Monatsletzten'
        [[of(2024, 1, 1), of(2025, 1, 31)]]                           | 2025 | 2     || false    | 'nach Ende'
        [[of(2024, 1, 1), of(2025, 2, 1)]]                            | 2025 | 2     || true     | 'Ende am Monatsersten'
        [[of(2025, 2, 28), null]]                                     | 2025 | 2     || true     | 'Beginn am Monatsletzten'
        [[of(2024, 1, 1), of(2024, 12, 31)]]                          | 2025 | 1     || false    | 'Jahreswechsel nach Austritt'
        [[of(2024, 1, 1), of(2024, 12, 31)]]                          | 2024 | 12    || true     | 'Austrittsmonat'
        // Ein- und Austritt schreiben new Date() mit Uhrzeit. Entscheidend ist der Beginn: 31.03. 20:00 liegt nach dem
        // Monatsletzten 00:00 – zählt nur, weil die Uhrzeit abgeschnitten wird.
        [[at(of(2025, 3, 31), 20), null]]                             | 2025 | 3     || true     | 'Beginn am Monatsletzten mit Uhrzeit'
        [[of(2024, 1, 1), at(of(2025, 1, 31), 18)]]                   | 2025 | 2     || false    | 'Ende mit Uhrzeit'
        [[of(2025, 1, 1), of(2025, 3, 31)], [of(2025, 5, 1), null]]   | 2025 | 4     || false    | 'Lücke zwischen zwei Mitgliedschaften'
        []                                                            | 2025 | 1     || false    | 'keine Historie'
    }

    def "Summe im Jahr #year ist #expected"() {
        expect:
        service.getTotalAmountForYear(betragsCarts(), year) == expected

        where:
        year || expected
        2024 || 15.5d   // 12,50 (31.12.2024) + 3,00 (20.01.2024)
        2025 || 2.25d   // 7,25 (01.01.2025) − 5,00 (Settlement-Empfänger 15.06.2025)
        2026 || 0d      // keine Carts
    }

    def "Jahres- und Monatssumme werden in Cent addiert: 0,10 + 0,20 ergibt 0,3 (DIV-29)"() {
        given:
        def carts = [0.1d, 0.2d].collect {
            TestData.cart(datePurchased: DateUtils.toDate(of(2025, 3, 1)), amount: it)
        }

        expect:
        service.getTotalAmountForYear(carts, 2025) == 0.3d
        service.getTotalAmountForMonth(carts, 2025, 3) == 0.3d
    }

    def "Summe im Monat #year-#month ist #expected"() {
        expect:
        service.getTotalAmountForMonth(betragsCarts(), year, month) == expected

        where:
        year | month || expected
        2025 | 1     || 7.25d   // nur 01.01.2025; 20.01.2024 ist ein anderes Jahr
        2024 | 12    || 12.5d   // Silvester gehört zu 2024
        2025 | 2     || 0d      // keine Carts
    }

    def "Monate mit Carts im Jahr #year: #expected"() {
        expect:
        service.getAvailableMonthsForYear(monatsCarts(), year) == expected

        where:
        year || expected
        2025 || [11, 3, 1]   // absteigend, März trotz zwei Carts nur einmal, 31.12.2024 nicht enthalten
        2023 || []
    }

    def "Monat #month heißt in '#locale' '#expected' (DIV-30)"() {
        expect:
        service.getMonthName(month, locale) == expected

        where:
        month | locale                      || expected
        3     | Locale.GERMAN               || 'März'
        3     | Locale.ENGLISH              || 'March'
        3     | Locale.forLanguageTag('es') || 'marzo'
        12    | Locale.ENGLISH              || 'December'
    }

    def "Carts im Jahr #year vorhanden (Carts gegeben: #mitCarts): #expected"() {
        given:
        def carts = mitCarts ? monatsCarts() : []

        expect:
        service.hasCartsForYear(carts, year) == expected

        where:
        mitCarts | year || expected
        true     | 2024 || true
        true     | 2025 || true
        true     | 2023 || false
        false    | 2025 || false
    }

    // Beträge exakt in double darstellbar, damit == ohne Toleranz gilt
    private static List<Cart> betragsCarts() {
        [(of(2024, 12, 31)): 12.5d,
         (of(2025, 1, 1)): 7.25d,
         (of(2025, 6, 15)): -5d,
         (of(2024, 1, 20)): 3d]
                .collect { day, amount -> TestData.cart(datePurchased: DateUtils.toDate(day), amount: amount) }
    }

    private static List<Cart> monatsCarts() {
        [of(2025, 1, 5),
         of(2025, 3, 1),
         of(2025, 3, 31),
         of(2025, 11, 30),
         of(2024, 12, 31)]
                .collect { TestData.cart(datePurchased: DateUtils.toDate(it)) }
    }

    private static Group gruppe(User owner, List<User> mitglieder) {
        TestData.group(id: GRUPPE_ID, owner: owner, members: mitglieder as Set)
    }

    private static GroupMembershipHistory mitgliedschaft(User user, LocalDate beginn, LocalDate ende) {
        TestData.membership(userId: user.id, groupId: GRUPPE_ID, membershipStart: DateUtils.toDate(beginn),
                membershipEnd: ende == null ? null : DateUtils.toDate(ende))
    }

    private static Cart einkauf(Long id, Group gruppe, User zahler, LocalDate tag) {
        TestData.cart(id: id, group: gruppe, user: zahler, amount: 10d, datePurchased: DateUtils.toDate(tag))
    }

    /** Angemeldeter Nutzer, Gruppe und verfügbare Jahre – Voraussetzungen beider Einstiegspunkte. */
    private void angemeldet(User user, Group gruppe, List<Integer> jahre) {
        dataLoaderService.getAuthenticatedUser() >> user
        dataLoaderService.loadGroup(GRUPPE_ID) >> gruppe
        cartRepository.getAvailableYearsForGroup(GRUPPE_ID) >> jahre
    }

    /** Carts, Historie und Nutzer ohne Aufrufzählung (auch die Pro-Nutzer-Historie, die der Service vor DIV-34 lud). */
    private void stammdaten(List<User> nutzer, List<GroupMembershipHistory> historie, List<Cart> carts) {
        dataLoaderService.loadCartListForGroup(GRUPPE_ID) >> carts
        dataLoaderService.loadMembershipHistoryForGroup(GRUPPE_ID) >> historie
        dataLoaderService.loadMembershipHistoryForGroupAndUser(GRUPPE_ID, _) >> { Long groupId, Long userId ->
            historie.findAll { it.userId == userId }
        }
        dataLoaderService.loadUser(_) >> { Long id -> nutzer.find { it.id == id } }
    }

    private static List<GroupMembershipHistory> history(List zeitraeume) {
        zeitraeume.collect { start, end ->
            TestData.membership(membershipStart: toDate(start), membershipEnd: end == null ? null : toDate(end))
        }
    }

    private static Date toDate(value) {
        value instanceof LocalDate ? DateUtils.toDate(value) : value as Date
    }

    /** Zeitpunkt mit Uhrzeit, wie ihn GroupMembershipHistoryService beim Ein- und Austritt mit new Date() schreibt. */
    private static Date at(LocalDate day, int hour) {
        Date.from(DateUtils.toDate(day).toInstant().plus(Duration.ofHours(hour)))
    }
}
