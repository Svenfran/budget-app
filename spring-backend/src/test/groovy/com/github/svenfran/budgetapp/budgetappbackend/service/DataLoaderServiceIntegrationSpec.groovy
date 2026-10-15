package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupMembershipHistoryRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.springframework.beans.factory.annotation.Autowired

import static com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData.REFERENCE_DATE

class DataLoaderServiceIntegrationSpec extends IntegrationSpec {

    @Autowired DataLoaderService dataLoaderService
    @Autowired GroupMembershipHistoryRepository membershipRepository

    def "Nutzer wird unabhängig von Schreibweise und Leerzeichen per E-Mail gefunden ('#gesucht')"() {
        given:
        def alfred = fixtures.user(email: 'alfred@divvy.test')

        expect:
        dataLoaderService.loadUserByEmail(gesucht).id == alfred.id

        where:
        gesucht << ['alfred@divvy.test', 'Alfred@Divvy.test', '  ALFRED@divvy.test ']
    }

    def "Nicht gefundene E-Mail: Meldung enthält die Eingabe unverändert (Frontend gleicht per Substring ab)"() {
        when:
        dataLoaderService.loadUserByEmail('Unbekannt@Divvy.test')

        then:
        def e = thrown(UserNotFoundException)
        e.message == 'User with email Unbekannt@Divvy.test not found'
    }

    def "Cart-Liste enthält nur Carts der Gruppe, neueste zuerst"() {
        given:
        def wg = fixtures.group()
        def andere = fixtures.group()
        def alt = fixtures.cart(wg, datePurchased: DateUtils.toDate(REFERENCE_DATE.minusDays(5)))
        def neu = fixtures.cart(wg, datePurchased: DateUtils.toDate(REFERENCE_DATE))
        def mitte = fixtures.cart(wg, datePurchased: DateUtils.toDate(REFERENCE_DATE.minusDays(2)))
        fixtures.cart(andere)

        expect:
        dataLoaderService.loadCartListForGroup(wg.group.id)*.id == [neu, mitte, alt]*.id
    }

    def "Kategorien werden je Gruppe geladen, nach Name sortiert"() {
        given:
        def wg = fixtures.group()
        def andere = fixtures.group()
        def wohnen = wg.category
        wohnen.name = 'Wohnen'
        def miete = fixtures.category(wg, name: 'Miete')
        def ausgehen = fixtures.category(wg, name: 'Ausgehen')
        fixtures.category(andere, name: 'Sport')
        flushAndClear()

        expect:
        dataLoaderService.loadCategoriesForGroup(wg.group.id)*.id == [ausgehen, miete, wohnen]*.id
    }

    def "Kategorie '#name' wird per Gruppe und Name gefunden: #gefunden"() {
        given:
        def wg = fixtures.group()
        def andere = fixtures.group()
        def lebensmittel = fixtures.category(wg, name: 'Lebensmittel')
        fixtures.category(andere, name: 'Sport')

        when:
        def result = dataLoaderService.loadCategoryByGroupAndName(wg.group, name)

        then:
        gefunden ? result.id == lebensmittel.id : result == null

        where:
        name           || gefunden
        'Lebensmittel' || true
        'Sport'        || false
        'Unbekannt'    || false
    }

    def "Mitgliederzahl zum Kaufdatum: #fall"() {
        given: "Owner und Ben sind seit REFERENCE_DATE - 1 Jahr Mitglied; Bens Zeitraum wird je Fall angepasst"
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def benMembership = membershipRepository.findByGroupIdAndUserId(wg.group.id, ben.id).first()
        if (benStart) benMembership.membershipStart = DateUtils.toDate(benStart)
        benMembership.membershipEnd = benEnde ? DateUtils.toDate(benEnde) : null
        membershipRepository.save(benMembership)
        flushAndClear()

        expect:
        dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(DateUtils.toDate(REFERENCE_DATE), wg.group.id) == erwartet

        where:
        fall                          | benStart                    | benEnde                      || erwartet
        'Owner und Mitglied'          | null                        | null                         || 2
        'Mitglied ausgetreten'        | null                        | REFERENCE_DATE.minusDays(10) || 1
        'Mitglied später eingetreten' | REFERENCE_DATE.plusDays(10) | null                         || 1
    }

    def "Ohne Mitgliedschaft zum Kaufdatum ist die Mitgliederzahl 0"() {
        given:
        def wg = fixtures.group()

        expect:
        dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(DateUtils.toDate(REFERENCE_DATE.minusYears(2)), wg.group.id) == 0
    }
}
