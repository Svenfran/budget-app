package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingListDto
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOrMemberOfGroupException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.ShoppingListDoesNotBelongToGroupException
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder

import static com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData.REFERENCE_DATE

/**
 * Einkaufsvorschläge einer Liste (DIV-23): nur aus den Käufen dieser Liste, ohne Käufe die Standardvorschläge, Artikel
 * der Liste ausgeschlossen, Rechte wie beim Laden der Listen. Rangfolge im Detail: ShoppingSuggestionRankerSpec.
 *
 * Ohne Request liest der Translator die JVM-Locale (lokal de, in CI ggf. en): Features mit Standardvorschlägen setzen
 * Locale.GERMAN und setzen sie im cleanup:-Block zurück.
 */
class ShoppingSuggestionServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(ShoppingSuggestionServiceIntegrationSpec)

    private static final List<String> STANDARD = ['Milch', 'Tomaten', 'Äpfel', 'Joghurt', 'Beeren', 'Bananen', 'Avocado']

    @Autowired ShoppingSuggestionService shoppingSuggestionService
    @Autowired ShoppingListService shoppingListService

    def "Vorschläge berücksichtigen nur die Käufe der angefragten Liste"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def edeka = fixtures.shoppingList(wg, name: 'Edeka')
        def baumarkt = fixtures.shoppingList(wg, name: 'Baumarkt')
        fixtures.purchase(edeka, itemName: 'Milch')
        fixtures.purchase(edeka, itemName: 'Brot')
        fixtures.purchase(baumarkt, itemName: 'Schrauben')
        def groupId = wg.group.id
        def (edekaId, baumarktId) = [edeka.id, baumarkt.id]
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {} "Edeka" mit Käufen Milch, Brot; Liste {} "Baumarkt" mit Kauf Schrauben',
                groupId, ben.id, edekaId, baumarktId)

        when:
        def vorschlaege = shoppingSuggestionService.getSuggestions(groupId, edekaId)
        LOG.info('shoppingSuggestionService.getSuggestions(Liste {}) -> standard {}, {}', edekaId, vorschlaege.defaultSuggestions, vorschlaege.names)

        then:
        !vorschlaege.defaultSuggestions
        vorschlaege.names as Set == ['Milch', 'Brot'] as Set
        vorschlaege.groupId == groupId
        vorschlaege.shoppingListId == edekaId
    }

    def "Nach dem Umbenennen der Liste bleiben die Vorschläge gleich"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg, name: 'Edeka')
        fixtures.purchase(liste, itemName: 'Milch', purchasedAt: DateUtils.toDate(REFERENCE_DATE.minusDays(1)))
        fixtures.purchase(liste, itemName: 'Brot', purchasedAt: DateUtils.toDate(REFERENCE_DATE))
        def groupId = wg.group.id
        def listId = liste.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {} "Edeka" mit Käufen Milch (gestern), Brot (heute) -> "Rewe"',
                groupId, ben.id, listId)

        when:
        def vorher = shoppingSuggestionService.getSuggestions(groupId, listId).names
        shoppingListService.updateShoppingList(new AddEditShoppingListDto(id: listId, name: 'Rewe', groupId: groupId))
        flushAndClear()
        def nachher = shoppingSuggestionService.getSuggestions(groupId, listId).names
        LOG.info('getSuggestions(Liste {}) vor dem Umbenennen {}, danach {}', listId, vorher, nachher)

        then:
        vorher == ['Brot', 'Milch']
        nachher == vorher
    }

    def "Liste ohne Käufe: Standardvorschläge ohne die Artikel der Liste"() {
        given:
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        fixtures.shoppingItem(liste, name: 'milch', completed: false)
        fixtures.shoppingItem(liste, name: 'Avocado', completed: true)
        def groupId = wg.group.id
        def listId = liste.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {} ohne Käufe mit Einträgen "milch" (offen), "Avocado" (erledigt); Locale de',
                groupId, ben.id, listId)

        when:
        def vorschlaege = shoppingSuggestionService.getSuggestions(groupId, listId)
        LOG.info('shoppingSuggestionService.getSuggestions(Liste {}) -> standard {}, {}', listId, vorschlaege.defaultSuggestions, vorschlaege.names)

        then:
        vorschlaege.defaultSuggestions
        vorschlaege.names == STANDARD - ['Milch', 'Avocado']

        cleanup:
        LocaleContextHolder.resetLocaleContext()
    }

    def "Artikel der Liste werden nicht vorgeschlagen, offen wie erledigt"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        ['Milch', 'Brot', 'Kaffee'].each { fixtures.purchase(liste, itemName: it) }
        fixtures.shoppingItem(liste, name: ' milch', completed: false)
        fixtures.shoppingItem(liste, name: 'BROT', completed: true)
        def groupId = wg.group.id
        def listId = liste.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {} mit Käufen Milch, Brot, Kaffee und Einträgen " milch" (offen), "BROT" (erledigt)',
                groupId, ben.id, listId)

        when:
        def vorschlaege = shoppingSuggestionService.getSuggestions(groupId, listId)
        LOG.info('shoppingSuggestionService.getSuggestions(Liste {}) -> standard {}, {}', listId, vorschlaege.defaultSuggestions, vorschlaege.names)

        then:
        !vorschlaege.defaultSuggestions
        vorschlaege.names == ['Kaffee']
    }

    def "Liste mit #fall: keine Vorschläge und keine Standardvorschläge"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        kaeufe.each { fixtures.purchase(liste, itemName: it) }
        eintraege.each { fixtures.shoppingItem(liste, name: it) }
        def groupId = wg.group.id
        def listId = liste.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Liste {} mit Käufen {} und Einträgen {}', groupId, ben.id, listId, kaeufe, eintraege)

        when:
        def vorschlaege = shoppingSuggestionService.getSuggestions(groupId, listId)
        LOG.info('shoppingSuggestionService.getSuggestions(Liste {}) -> standard {}, {}', listId, vorschlaege.defaultSuggestions, vorschlaege.names)

        then:
        !vorschlaege.defaultSuggestions
        vorschlaege.names == []

        where:
        fall                                  | kaeufe          | eintraege
        'Käufen, die alle auf der Liste stehen' | ['Milch']       | ['Milch']
        'nur namenlosen Käufen'               | [null, '  ']    | []
    }

    def "Neue Liste mit dem Namen einer gelöschten Liste übernimmt deren Historie nicht"() {
        given:
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def alt = fixtures.shoppingList(wg, name: 'Edeka')
        fixtures.purchase(alt, itemName: 'Kaffee')
        def groupId = wg.group.id
        def altId = alt.id
        flushAndClear()
        loginAs(ben)
        shoppingListService.deleteShoppingList(new AddEditShoppingListDto(id: altId, groupId: groupId))
        def neuId = shoppingListService.addShoppingList(new AddEditShoppingListDto(name: 'Edeka', groupId: groupId)).id
        flushAndClear()
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet); Liste {} "Edeka" mit Kauf Kaffee gelöscht, neue Liste {} "Edeka"; Locale de',
                groupId, ben.id, altId, neuId)

        when:
        def vorschlaege = shoppingSuggestionService.getSuggestions(groupId, neuId)
        LOG.info('shoppingSuggestionService.getSuggestions(Liste {}) -> standard {}, {}', neuId, vorschlaege.defaultSuggestions, vorschlaege.names)

        then:
        vorschlaege.defaultSuggestions
        vorschlaege.names == STANDARD

        cleanup:
        LocaleContextHolder.resetLocaleContext()
    }

    def "Nicht-Mitglied darf keine Vorschläge abrufen"() {
        given:
        def fremder = fixtures.user()
        def wg = fixtures.group()
        def liste = fixtures.shoppingList(wg)
        fixtures.purchase(liste, itemName: 'Milch')
        def groupId = wg.group.id
        def listId = liste.id
        flushAndClear()
        loginAs(fremder)
        LOG.info('Szenario: Gruppe {}, Liste {} mit Kauf; angemeldet Außenstehender {}', groupId, listId, fremder.id)

        when:
        LOG.info('shoppingSuggestionService.getSuggestions(Liste {}), erwartet NotOwnerOrMemberOfGroupException', listId)
        shoppingSuggestionService.getSuggestions(groupId, listId)

        then:
        thrown(NotOwnerOrMemberOfGroupException)
    }

    def "Liste einer fremden Gruppe wird abgewiesen"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def fremdeListe = fixtures.shoppingList(fixtures.group())
        fixtures.purchase(fremdeListe, itemName: 'Milch')
        def groupId = wg.group.id
        def fremdeListId = fremdeListe.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet); Liste {} gehört einer anderen Gruppe', groupId, ben.id, fremdeListId)

        when:
        LOG.info('shoppingSuggestionService.getSuggestions(Gruppe {}, Liste {}), erwartet ShoppingListDoesNotBelongToGroupException', groupId, fremdeListId)
        shoppingSuggestionService.getSuggestions(groupId, fremdeListId)

        then:
        thrown(ShoppingListDoesNotBelongToGroupException)
    }
}
