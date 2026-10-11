package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupMembershipHistoryRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.springframework.aop.AopInvocationException
import org.springframework.beans.factory.annotation.Autowired

import static com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData.REFERENCE_DATE

class DataLoaderServiceIntegrationSpec extends IntegrationSpec {

    @Autowired DataLoaderService dataLoaderService
    @Autowired GroupMembershipHistoryRepository membershipRepository

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
        def miete = fixtures.category(wg, name: 'Miete')
        def ausgehen = fixtures.category(wg, name: 'Ausgehen')
        fixtures.category(andere, name: 'Sport')

        expect:
        dataLoaderService.loadCategoriesForGroup(wg.group.id)*.id == [ausgehen, wg.category, miete]*.id
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

    /*
     * Ist-Verhalten (Konzept Q3): Ohne Mitgliedschaft zum Kaufdatum liefert die Query keine Zeile, und Spring Data
     * kann das nicht auf den int-Rückgabetyp abbilden. Fachlich muss das Kaufdatum immer im Mitgliedszeitraum liegen;
     * addCart/updateCart/addSettlementPayment prüfen das, die wiederkehrenden Carts und GroupService.calculateCarts
     * nicht. Korrektur in DIV-21.
     */
    def "Ohne Mitgliedschaft zum Kaufdatum schlägt die Abfrage der Mitgliederzahl fehl"() {
        given:
        def wg = fixtures.group()

        when:
        dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(DateUtils.toDate(REFERENCE_DATE.minusYears(2)), wg.group.id)

        then:
        thrown(AopInvocationException)
    }
}
