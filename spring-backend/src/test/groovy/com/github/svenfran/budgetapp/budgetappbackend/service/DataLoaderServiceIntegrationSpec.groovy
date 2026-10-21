package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupMembershipHistoryRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

import static com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData.REFERENCE_DATE

class DataLoaderServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(DataLoaderServiceIntegrationSpec)

    @Autowired DataLoaderService dataLoaderService
    @Autowired GroupMembershipHistoryRepository membershipRepository

    def "Nutzer wird unabhängig von Schreibweise und Leerzeichen per E-Mail gefunden ('#gesucht')"() {
        given:
        def alfred = fixtures.user(email: 'alfred@divvy.test')
        LOG.info('Szenario: User {} mit alfred@divvy.test', alfred.id)

        when:
        def gefundenId = dataLoaderService.loadUserByEmail(gesucht).id
        LOG.info('dataLoaderService.loadUserByEmail(\'{}\') -> User {}', gesucht, gefundenId)

        then:
        gefundenId == alfred.id

        where:
        gesucht << ['alfred@divvy.test', 'Alfred@Divvy.test', '  ALFRED@divvy.test ']
    }

    def "Nicht gefundene E-Mail: Meldung enthält die Eingabe unverändert (Frontend gleicht per Substring ab)"() {
        given:
        LOG.info('Szenario: kein Nutzer mit Unbekannt@Divvy.test')

        when:
        LOG.info('dataLoaderService.loadUserByEmail(\'Unbekannt@Divvy.test\'), erwartet UserNotFoundException')
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
        def fremd = fixtures.cart(andere)
        LOG.info('Szenario: Gruppe {} mit Einkäufen neu {} (Referenzdatum), mitte {} (-2 Tage), alt {} (-5 Tage); ' +
                'Gruppe {} mit Einkauf {}', wg.group.id, neu.id, mitte.id, alt.id, andere.group.id, fremd.id)

        when:
        def cartIds = dataLoaderService.loadCartListForGroup(wg.group.id)*.id
        LOG.info('dataLoaderService.loadCartListForGroup(Gruppe {}) -> Einkäufe {}', wg.group.id, cartIds)

        then:
        cartIds == [neu, mitte, alt]*.id
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
        LOG.info('Szenario: Gruppe {} mit Kategorien Wohnen {}, Miete {}, Ausgehen {}; Gruppe {} mit Sport',
                wg.group.id, wohnen.id, miete.id, ausgehen.id, andere.group.id)

        when:
        def categoryIds = dataLoaderService.loadCategoriesForGroup(wg.group.id)*.id
        LOG.info('dataLoaderService.loadCategoriesForGroup(Gruppe {}) -> Kategorien {}', wg.group.id, categoryIds)

        then:
        categoryIds == [ausgehen, miete, wohnen]*.id
    }

    def "Kategorie '#name' wird per Gruppe und Name gefunden: #gefunden"() {
        given:
        def wg = fixtures.group()
        def andere = fixtures.group()
        def lebensmittel = fixtures.category(wg, name: 'Lebensmittel')
        fixtures.category(andere, name: 'Sport')
        LOG.info('Szenario: Gruppe {} mit Lebensmittel {}, Gruppe {} mit Sport', wg.group.id, lebensmittel.id, andere.group.id)

        when:
        def result = dataLoaderService.loadCategoryByGroupAndName(wg.group, name)
        LOG.info('dataLoaderService.loadCategoryByGroupAndName(Gruppe {}, \'{}\') -> Kategorie {}', wg.group.id, name, result?.id)

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
        LOG.info('Szenario: Gruppe {}, Owner {}, Ben {} (Beginn {}, Ende {}), Kaufdatum {}', wg.group.id, wg.owner.id, ben.id,
                benStart ?: 'Standard', benEnde ?: 'offen', REFERENCE_DATE)

        when:
        def anzahl = dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(DateUtils.toDate(REFERENCE_DATE), wg.group.id)
        LOG.info('dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup({}, Gruppe {}) -> {}', REFERENCE_DATE,
                wg.group.id, anzahl)

        then:
        anzahl == erwartet

        where:
        fall                          | benStart                    | benEnde                      || erwartet
        'Owner und Mitglied'          | null                        | null                         || 2
        'Mitglied ausgetreten'        | null                        | REFERENCE_DATE.minusDays(10) || 1
        'Mitglied später eingetreten' | REFERENCE_DATE.plusDays(10) | null                         || 1
    }

    def "Ohne Mitgliedschaft zum Kaufdatum ist die Mitgliederzahl 0"() {
        given:
        def wg = fixtures.group()
        LOG.info('Szenario: Gruppe {}, Mitgliedschaften ab {}, Kaufdatum {}', wg.group.id, REFERENCE_DATE.minusYears(1),
                REFERENCE_DATE.minusYears(2))

        when:
        def anzahl = dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup(DateUtils.toDate(REFERENCE_DATE.minusYears(2)), wg.group.id)
        LOG.info('dataLoaderService.getMemberCountForCartByDatePurchasedAndGroup({}, Gruppe {}) -> {}',
                REFERENCE_DATE.minusYears(2), wg.group.id, anzahl)

        then:
        anzahl == 0
    }
}
