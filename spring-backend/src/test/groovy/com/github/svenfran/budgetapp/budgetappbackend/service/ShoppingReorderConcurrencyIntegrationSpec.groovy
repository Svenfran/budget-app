package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingItemDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.AddEditShoppingListDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.MoveShoppingItemDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.ShoppingItemOrderDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.ShoppingListDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.ShoppingListOrderDto
import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.ShoppingItemDoesNotBelongToShoppingListException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.ShoppingListNotFoundException
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingItemRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingListRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.DatabaseCleaner
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import spock.util.concurrent.PollingConditions

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Gleichzeitige Änderungen an der Reihenfolge der Einkaufslisten (DIV-1): Alle Reorders und Anlagen einer Gruppe
 * sperren zuerst die Gruppenzeile und laufen dadurch nacheinander; es gilt die zuletzt committete Reihenfolge (AC14),
 * Listen- und Item-Reorder blockieren sich nicht kreuzweise, neue Einträge landen am Ende (AC7). Verschieben zwischen
 * Listen (DIV-2) nutzt dieselbe Sperre: parallele Änderungen, Löschungen und das Laden sehen den Move ganz oder gar nicht.
 *
 * Ablauf je Feature (overlapping): Transaktion 1 führt ihren Aufruf aus und hält die Transaktion offen; Transaktion 2
 * startet in einem zweiten Thread und muss an der Gruppensperre warten (geprüft über pg_stat_activity, ohne sleep);
 * danach committet 1, dann 2. Jeder Thread meldet sich selbst an (SecurityContext ist thread-lokal).
 *
 * Commit-abhängig: keine Test-Transaktion, Aufräumen vor jedem Feature; Transaktionen über TransactionTemplate.
 * Fixtures legen Listen und Einträge mit Position 0 an (wie Altdaten mit gleicher Position); Tie-Breaker ist die id.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ShoppingReorderConcurrencyIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(ShoppingReorderConcurrencyIntegrationSpec)

    /** Sicherheitstimeout in Sekunden für Latches, das Warten auf die Sperre und die Threads. */
    private static final long TIMEOUT = 20

    @Autowired ShoppingListService shoppingListService
    @Autowired ShoppingItemService shoppingItemService
    @Autowired ShoppingListRepository shoppingListRepository
    @Autowired ShoppingItemRepository shoppingItemRepository
    @Autowired GroupRepository groupRepository
    @Autowired PlatformTransactionManager transactionManager
    @Autowired JdbcTemplate jdbcTemplate
    @Autowired DatabaseCleaner databaseCleaner

    TransactionTemplate transaction

    def setup() {
        databaseCleaner.truncateAll()
        transaction = new TransactionTemplate(transactionManager)
    }

    def "Zwei Listen-Reorders: der zweite wartet an der Gruppensperre, gespeichert ist seine Reihenfolge"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def (a, b, c) = (1..3).collect { fixtures.shoppingList(wg).id }
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Listen A {}, B {}, C {}; T1 [C,B,A] hält offen, T2 [B,A,C]', groupId, ben.id, a, b, c)

        when:
        def (erste, zweite) = overlapping(ben,
                { shoppingListService.reorderShoppingLists(new ShoppingListOrderDto(groupId: groupId, shoppingListIds: [c, b, a])) },
                { shoppingListService.reorderShoppingLists(new ShoppingListOrderDto(groupId: groupId, shoppingListIds: [b, a, c])) })
        def gespeichert = listIds(groupId)
        def revision = groupRepository.findListOrderRevision(groupId)
        LOG.info('T1 -> revision {}, {}; T2 -> revision {}, {}; gespeichert {}, listOrderRevision {}',
                erste.revision, erste.shoppingListIds, zweite.revision, zweite.shoppingListIds, gespeichert, revision)

        then:
        erste.shoppingListIds == [c, b, a]
        zweite.shoppingListIds == [b, a, c]
        gespeichert == [b, a, c]
        erste.revision == 1L
        zweite.revision == 2L
        revision == 2L
    }

    def "Zwei Item-Reorders derselben Liste: der zweite wartet an der Gruppensperre, gespeichert ist seine Reihenfolge"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def groupId = wg.group.id
        def listId = liste.id
        def (x, y, z) = (1..3).collect { fixtures.shoppingItem(liste).id }
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste {} mit Einträgen X {}, Y {}, Z {}; T1 [Z,Y,X] hält offen, T2 [Y,X,Z]',
                groupId, ben.id, listId, x, y, z)

        when:
        def (erste, zweite) = overlapping(ben,
                { shoppingItemService.reorderShoppingItems(new ShoppingItemOrderDto(groupId: groupId, shoppingListId: listId, shoppingItemIds: [z, y, x])) },
                { shoppingItemService.reorderShoppingItems(new ShoppingItemOrderDto(groupId: groupId, shoppingListId: listId, shoppingItemIds: [y, x, z])) })
        def gespeichert = itemIds(listId)
        def revision = shoppingListRepository.findItemOrderRevision(listId)
        LOG.info('T1 -> revision {}, {}; T2 -> revision {}, {}; gespeichert {}, itemOrderRevision {}',
                erste.revision, erste.shoppingItemIds, zweite.revision, zweite.shoppingItemIds, gespeichert, revision)

        then:
        gespeichert == [y, x, z]
        erste.revision == 1L
        zweite.revision == 2L
        revision == 2L
    }

    def "Listen- und Item-Reorder überlappend (#zuerst zuerst): kein Deadlock, beide Reihenfolgen und Revisionen gespeichert"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def listen = (1..2).collect { fixtures.shoppingList(wg) }
        def (a, b) = listen*.id
        def (x, y) = (1..2).collect { fixtures.shoppingItem(listen[0]).id }
        def listenReorder = { shoppingListService.reorderShoppingLists(new ShoppingListOrderDto(groupId: groupId, shoppingListIds: [b, a])) }
        def itemReorder = { shoppingItemService.reorderShoppingItems(new ShoppingItemOrderDto(groupId: groupId, shoppingListId: a, shoppingItemIds: [y, x])) }
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Listen A {}, B {}; Liste A mit Einträgen X {}, Y {}; {} zuerst (hält offen)',
                groupId, ben.id, a, b, x, y, zuerst)

        when:
        def ergebnisse = zuerst == 'Listen' ? overlapping(ben, listenReorder, itemReorder) : overlapping(ben, itemReorder, listenReorder)
        def gespeicherteListen = listIds(groupId)
        def gespeicherteEintraege = itemIds(a)
        def listRevision = groupRepository.findListOrderRevision(groupId)
        def itemRevision = shoppingListRepository.findItemOrderRevision(a)
        LOG.info('beide committet ({} Ergebnisse) -> Listen {} (Revision {}), Einträge {} (Revision {})',
                ergebnisse.size(), gespeicherteListen, listRevision, gespeicherteEintraege, itemRevision)

        then:
        gespeicherteListen == [b, a]
        gespeicherteEintraege == [y, x]
        listRevision == 1L
        itemRevision == 1L

        where:
        zuerst << ['Listen', 'Items']
    }

    def "Umbenennen nach einem Reorder lässt die gespeicherte Position unverändert"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def (a, b, c) = (1..3).collect { fixtures.shoppingList(wg).id }
        loginAs(ben)
        LOG.info('Szenario: Gruppe {}, Mitglied {} (angemeldet), Listen A {}, B {}, C {}; Reorder [C,A,B], danach A umbenennen',
                groupId, ben.id, a, b, c)

        when:
        transaction.execute { shoppingListService.reorderShoppingLists(new ShoppingListOrderDto(groupId: groupId, shoppingListIds: [c, a, b])) }
        transaction.execute { shoppingListService.updateShoppingList(new AddEditShoppingListDto(id: a, name: 'Neu', groupId: groupId)) }
        def gespeichert = listIds(groupId)
        def aPosition = shoppingListRepository.findById(a).get().sortOrder
        LOG.info('Reorder und updateShoppingList(Liste {}) committet -> gespeichert {}, Position von A {}', a, gespeichert, aPosition)

        then:
        gespeichert == [c, a, b]
        aPosition == 1
    }

    def "Listen: Anlage hält offen, Reorder wartet und normalisiert die neue Liste mit"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def (a, b, c) = (1..3).collect { fixtures.shoppingList(wg).id }
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Listen A {}, B {}, C {} (alle Position 0); T1 legt D an und hält offen, T2 Reorder [C,B,A]',
                groupId, ben.id, a, b, c)

        when:
        def (anlage, reorder) = overlapping(ben,
                { shoppingListService.addShoppingList(new AddEditShoppingListDto(name: 'D', groupId: groupId)) },
                { shoppingListService.reorderShoppingLists(new ShoppingListOrderDto(groupId: groupId, shoppingListIds: [c, b, a])) })
        def d = anlage.id
        def gespeichert = listIds(groupId)
        def geladen = geladen(ben, groupId)*.id
        LOG.info('T1 -> Liste D {}; T2 -> {}; gespeichert {}, Laden {}', d, reorder.shoppingListIds, gespeichert, geladen)

        then: "D war beim Reorder committet und steht in seiner Antwort"
        reorder.shoppingListIds == [c, b, a, d]
        gespeichert == [c, b, a, d]
        geladen == gespeichert
    }

    def "Listen: Reorder hält offen, Anlage wartet und landet am Ende"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def (a, b, c) = (1..3).collect { fixtures.shoppingList(wg).id }
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Listen A {}, B {}, C {} (alle Position 0); T1 Reorder [C,B,A] hält offen, T2 legt D an',
                groupId, ben.id, a, b, c)

        when:
        def (reorder, anlage) = overlapping(ben,
                { shoppingListService.reorderShoppingLists(new ShoppingListOrderDto(groupId: groupId, shoppingListIds: [c, b, a])) },
                { shoppingListService.addShoppingList(new AddEditShoppingListDto(name: 'D', groupId: groupId)) })
        def d = anlage.id
        def gespeichert = listIds(groupId)
        def dPosition = shoppingListRepository.findById(d).get().sortOrder
        def geladen = geladen(ben, groupId)*.id
        LOG.info('T1 -> {}; T2 -> Liste D {} an Position {}; gespeichert {}, Laden {}', reorder.shoppingListIds, d, dPosition, gespeichert, geladen)

        then: "D war beim Reorder nicht committet und fehlt in seiner Antwort"
        reorder.shoppingListIds == [c, b, a]
        gespeichert == [c, b, a, d]
        dPosition == 3
        geladen == gespeichert
    }

    def "Einträge: Anlage hält offen, Reorder wartet und normalisiert den neuen Eintrag mit"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def groupId = wg.group.id
        def listId = liste.id
        def (x, y, z) = (1..3).collect { fixtures.shoppingItem(liste).id }
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste {} mit Einträgen X {}, Y {}, Z {} (alle Position 0); T1 legt W an und hält offen, T2 Reorder [Z,Y,X]',
                groupId, ben.id, listId, x, y, z)

        when:
        def (anlage, reorder) = overlapping(ben,
                { shoppingItemService.addShoppingItem(new AddEditShoppingItemDto(name: 'W', shoppingListId: listId, groupId: groupId)) },
                { shoppingItemService.reorderShoppingItems(new ShoppingItemOrderDto(groupId: groupId, shoppingListId: listId, shoppingItemIds: [z, y, x])) })
        def w = anlage.id
        def gespeichert = itemIds(listId)
        def geladen = geladen(ben, groupId)[0].shoppingItems*.id
        LOG.info('T1 -> Eintrag W {}; T2 -> {}; gespeichert {}, Laden {}', w, reorder.shoppingItemIds, gespeichert, geladen)

        then: "W war beim Reorder committet und steht in seiner Antwort"
        reorder.shoppingItemIds == [z, y, x, w]
        gespeichert == [z, y, x, w]
        geladen == gespeichert
    }

    def "Einträge: Reorder hält offen, Anlage wartet und landet am Ende"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def liste = fixtures.shoppingList(wg)
        def groupId = wg.group.id
        def listId = liste.id
        def (x, y, z) = (1..3).collect { fixtures.shoppingItem(liste).id }
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste {} mit Einträgen X {}, Y {}, Z {} (alle Position 0); T1 Reorder [Z,Y,X] hält offen, T2 legt W an',
                groupId, ben.id, listId, x, y, z)

        when:
        def (reorder, anlage) = overlapping(ben,
                { shoppingItemService.reorderShoppingItems(new ShoppingItemOrderDto(groupId: groupId, shoppingListId: listId, shoppingItemIds: [z, y, x])) },
                { shoppingItemService.addShoppingItem(new AddEditShoppingItemDto(name: 'W', shoppingListId: listId, groupId: groupId)) })
        def w = anlage.id
        def gespeichert = itemIds(listId)
        def wPosition = shoppingItemRepository.findById(w).get().sortOrder
        def geladen = geladen(ben, groupId)[0].shoppingItems*.id
        LOG.info('T1 -> {}; T2 -> Eintrag W {} an Position {}; gespeichert {}, Laden {}', reorder.shoppingItemIds, w, wPosition, gespeichert, geladen)

        then: "W war beim Reorder nicht committet und fehlt in seiner Antwort"
        reorder.shoppingItemIds == [z, y, x]
        gespeichert == [z, y, x, w]
        wPosition == 3
        geladen == gespeichert
    }

    // Verschieben zwischen Listen (DIV-2)

    def "Abhaken mit altem Listenstand wartet auf den Move und setzt die Zuordnung nicht zurück"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def (a, b) = (1..2).collect { fixtures.shoppingList(wg) }
        def (aId, bId) = [a.id, b.id]
        def xId = fixtures.shoppingItem(a, name: 'Milch', completed: false).id
        def uId = fixtures.shoppingItem(b).id
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste A {} mit X {} "Milch", Liste B {} mit U {}; T1 verschiebt X nach B und hält offen, T2 hakt X mit Liste A ab',
                groupId, ben.id, aId, xId, bId, uId)

        when:
        overlapping(ben,
                { shoppingItemService.moveShoppingItem(new MoveShoppingItemDto(groupId, xId, aId, bId, [uId, xId])) },
                { shoppingItemService.updateShoppingItem(new AddEditShoppingItemDto(id: xId, name: 'Milch', completed: true, shoppingListId: aId, groupId: groupId)) })
        def (inA, inB) = [aId, bId].collect { itemIds(it) }
        def completed = shoppingItemRepository.findById(xId).get().completed
        LOG.info('beide committet -> A {}, B {}, X erledigt {}', inA, inB, completed)

        then:
        inA == []
        inB == [uId, xId]
        completed
    }

    def "Zwei Moves desselben Eintrags: der zweite wartet und wird abgewiesen, der Eintrag steht genau einmal in der Liste des ersten"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def (a, b, c) = (1..3).collect { fixtures.shoppingList(wg) }
        def (aId, bId, cId) = [a.id, b.id, c.id]
        def xId = fixtures.shoppingItem(a).id
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Listen A {} mit X {}, B {}, C {}; T1 verschiebt X nach B und hält offen, T2 verschiebt X von A nach C',
                groupId, ben.id, aId, xId, bId, cId)

        when:
        def (erste, zweite) = overlapping(ben,
                { shoppingItemService.moveShoppingItem(new MoveShoppingItemDto(groupId, xId, aId, bId, [xId])) },
                { abgefangen { shoppingItemService.moveShoppingItem(new MoveShoppingItemDto(groupId, xId, aId, cId, [xId])) } })
        def (inA, inB, inC) = [aId, bId, cId].collect { itemIds(it) }
        def revisionen = [aId, bId, cId].collect { shoppingListRepository.findItemOrderRevision(it) }
        LOG.info('T1 -> target {}; T2 -> {}; gespeichert A {}, B {}, C {}, Revisionen {}', erste.target.shoppingItemIds, zweite.class.simpleName, inA, inB, inC, revisionen)

        then:
        zweite instanceof ShoppingItemDoesNotBelongToShoppingListException
        inA == []
        inB == [xId]
        inC == []
        revisionen == [1L, 1L, 0L]
    }

    def "Move und Item-Reorder der Zielliste überlappend (#zuerst zuerst): kein Deadlock, Reihenfolge und Revisionen konsistent"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def (a, b) = (1..2).collect { fixtures.shoppingList(wg) }
        def (aId, bId) = [a.id, b.id]
        def xId = fixtures.shoppingItem(a).id
        def (uId, vId) = (1..2).collect { fixtures.shoppingItem(b, sortOrder: it).id }
        def move = { shoppingItemService.moveShoppingItem(new MoveShoppingItemDto(groupId, xId, aId, bId, [xId, uId, vId])) }
        def reorder = { shoppingItemService.reorderShoppingItems(new ShoppingItemOrderDto(groupId: groupId, shoppingListId: bId, shoppingItemIds: [vId, uId])) }
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste A {} mit X {}, Liste B {} mit U {}, V {}; Move X an den Anfang von B, Reorder B [V,U]; {} zuerst (hält offen)',
                groupId, ben.id, aId, xId, bId, uId, vId, zuerst)

        when:
        zuerst == 'Move' ? overlapping(ben, move, reorder) : overlapping(ben, reorder, move)
        def inB = itemIds(bId)
        def (revisionA, revisionB) = [aId, bId].collect { shoppingListRepository.findItemOrderRevision(it) }
        LOG.info('beide committet -> B {}, Revisionen A {}, B {}', inB, revisionA, revisionB)

        then:
        inB == erwartet.collect { [X: xId, U: uId, V: vId][it] }
        revisionA == 1L
        revisionB == 2L

        where:
        zuerst    || erwartet
        'Move'    || ['V', 'U', 'X']
        'Reorder' || ['X', 'U', 'V']
    }

    def "Zielliste wird gelöscht, während der Move wartet: der Move wird mit ShoppingListNotFoundException abgewiesen, nichts ändert sich"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def (a, b) = (1..2).collect { fixtures.shoppingList(wg) }
        def (aId, bId) = [a.id, b.id]
        def (xId, pId) = (0..1).collect { fixtures.shoppingItem(a, sortOrder: it).id }
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste A {} mit X {}, P {}, Liste B {}; T1 löscht B und hält offen, T2 verschiebt X nach B',
                groupId, ben.id, aId, xId, pId, bId)

        when:
        def (loeschen, move) = overlapping(ben,
                { shoppingListService.deleteShoppingList(new AddEditShoppingListDto(id: bId, groupId: groupId)) },
                { abgefangen { shoppingItemService.moveShoppingItem(new MoveShoppingItemDto(groupId, xId, aId, bId, [xId])) } })
        def inA = itemIds(aId)
        def revisionA = shoppingListRepository.findItemOrderRevision(aId)
        def bVorhanden = shoppingListRepository.existsById(bId)
        LOG.info('beide committet -> T2 {}; A {} (Revision {}), B vorhanden {}', move.class.simpleName, inA, revisionA, bVorhanden)

        then:
        move instanceof ShoppingListNotFoundException
        inA == [xId, pId]
        revisionA == 0L
        !bVorhanden
    }

    def "Quellliste wird gelöscht, während der Move die Sperre hält: der verschobene Eintrag bleibt in der Zielliste erhalten"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def (a, b) = (1..2).collect { fixtures.shoppingList(wg) }
        def (aId, bId) = [a.id, b.id]
        def (xId, pId) = (0..1).collect { fixtures.shoppingItem(a, sortOrder: it).id }
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste A {} mit X {}, P {}, Liste B {}; T1 verschiebt X nach B und hält offen, T2 löscht A',
                groupId, ben.id, aId, xId, pId, bId)

        when:
        overlapping(ben,
                { shoppingItemService.moveShoppingItem(new MoveShoppingItemDto(groupId, xId, aId, bId, [xId])) },
                { shoppingListService.deleteShoppingList(new AddEditShoppingListDto(id: aId, groupId: groupId)) })
        def listen = listIds(groupId)
        def inB = itemIds(bId)
        def pVorhanden = shoppingItemRepository.existsById(pId)
        LOG.info('beide committet -> Listen {}, B {}, P vorhanden {}', listen, inB, pVorhanden)

        then:
        listen == [bId]
        inB == [xId]
        !pVorhanden
    }

    def "Zielliste wird nach dem Move-Commit gelöscht: der verschobene Eintrag wird mit ihr gelöscht, die Quellliste bleibt"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def (a, b) = (1..2).collect { fixtures.shoppingList(wg) }
        def (aId, bId) = [a.id, b.id]
        def (xId, pId) = (0..1).collect { fixtures.shoppingItem(a, sortOrder: it).id }
        def uId = fixtures.shoppingItem(b).id
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Liste A {} mit X {}, P {}, Liste B {} mit U {}; T1 verschiebt X nach B und hält offen, T2 löscht B',
                groupId, ben.id, aId, xId, pId, bId, uId)

        when:
        overlapping(ben,
                { shoppingItemService.moveShoppingItem(new MoveShoppingItemDto(groupId, xId, aId, bId, [xId, uId])) },
                { shoppingListService.deleteShoppingList(new AddEditShoppingListDto(id: bId, groupId: groupId)) })
        def listen = listIds(groupId)
        def inA = itemIds(aId)
        def verbliebeneEintraege = [xId, pId, uId].findAll { shoppingItemRepository.existsById(it) }
        LOG.info('beide committet -> Listen {}, A {}, verbliebene Einträge {}', listen, inA, verbliebeneEintraege)

        then:
        listen == [aId]
        inA == [pId]
        verbliebeneEintraege == [pId]
    }

    def "Laden liest einen einheitlichen Stand: ein während des Ladens committeter Move (#richtung) erscheint nicht in der Antwort"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def listen = [A: fixtures.shoppingList(wg, sortOrder: 0), B: fixtures.shoppingList(wg, sortOrder: 1)]
        def (aId, bId) = [listen.A.id, listen.B.id]
        def xId = fixtures.shoppingItem(listen[quelle], sortOrder: 0).id
        def pId = fixtures.shoppingItem(listen.A, sortOrder: 1).id
        def uId = fixtures.shoppingItem(listen.B, sortOrder: 1).id
        def vorher = quelle == 'A' ? [(aId): [xId, pId], (bId): [uId]] : [(aId): [pId], (bId): [xId, uId]]
        def (quellId, zielId) = [listen[quelle].id, listen[ziel].id]
        def zielBestand = vorher[zielId]
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Listen A {}, B {}, Einträge {}; T1 sperrt shoppingitem, das Laden wartet darauf, T1 verschiebt X {} von {} nach {} und committet',
                groupId, ben.id, aId, bId, vorher, xId, quelle, ziel)

        when:
        def antwort = ladenWaehrend(ben, groupId) {
            shoppingItemService.moveShoppingItem(new MoveShoppingItemDto(groupId, xId, quellId, zielId, [xId] + zielBestand))
        }
        def geladen = antwort.collectEntries { [it.id, it.shoppingItems*.id] }
        def revisionen = antwort*.itemOrderRevision
        def gespeichert = [(aId): itemIds(aId), (bId): itemIds(bId)]
        LOG.info('getShoppingListsForGroup -> {}, itemOrderRevision {}; gespeichert nach dem Move {}', geladen, revisionen, gespeichert)

        then: "die Antwort ist vollständig der Stand vor dem Move, gespeichert ist der Stand danach"
        geladen == vorher
        revisionen == [0L, 0L]
        gespeichert[quellId] == vorher[quellId] - xId
        gespeichert[zielId] == [xId] + zielBestand

        where:
        richtung | quelle | ziel
        'A -> B' | 'A'    | 'B'
        'B -> A' | 'B'    | 'A'
    }

    /**
     * Lädt die Listen über den Service-Proxy ohne äußere Transaktion, während Transaktion 1 die Tabelle shoppingitem
     * exklusiv sperrt: Das Laden liest Revision und Listen und wartet beim Nachladen der Einträge (geprüft über
     * pg_stat_activity). Erst dann führt Transaktion 1 {@code aenderung} aus und committet. Liefert die Antwort des
     * Ladens.
     */
    private List<ShoppingListDto> ladenWaehrend(User user, Long groupId, Closure aenderung) {
        def gesperrt = new CountDownLatch(1)
        def aenderungFreigabe = new CountDownLatch(1)
        def pool = Executors.newFixedThreadPool(2)
        try {
            def t1 = pool.submit({
                inTransaction(user) {
                    jdbcTemplate.execute('LOCK TABLE shoppingitem IN ACCESS EXCLUSIVE MODE')
                    gesperrt.countDown()
                    assert aenderungFreigabe.await(TIMEOUT, TimeUnit.SECONDS)
                    aenderung.call()
                }
            } as Callable)
            assert gesperrt.await(TIMEOUT, TimeUnit.SECONDS)
            def laden = pool.submit({
                loginAs(user)
                try {
                    shoppingListService.getShoppingListsForGroup(groupId)
                } finally {
                    SecurityContextHolder.clearContext()
                }
            } as Callable)
            new PollingConditions(timeout: TIMEOUT).eventually {
                assert wartendAufSperre() == 1
            }
            aenderungFreigabe.countDown()
            t1.get(TIMEOUT, TimeUnit.SECONDS)
            laden.get(TIMEOUT, TimeUnit.SECONDS) as List<ShoppingListDto>
        } finally {
            aenderungFreigabe.countDown()
            pool.shutdownNow()
        }
    }

    /** Führt {@code aufruf} aus und liefert eine geworfene Exception als Ergebnis (die Transaktion committet). */
    private static Object abgefangen(Closure aufruf) {
        try {
            aufruf.call()
        } catch (Exception e) {
            e
        }
    }

    /**
     * Führt {@code erster} in Transaktion 1 aus und hält sie offen, startet {@code zweiter} in Transaktion 2 (eigener
     * Thread), wartet, bis Transaktion 2 an einer Zeilensperre hängt, und gibt dann den Commit von 1 frei. Liefert
     * die Ergebnisse beider Aufrufe; ein Fehler in einem Thread scheitert über Future.get.
     */
    private List overlapping(User user, Closure erster, Closure zweiter) {
        def ersterAusgefuehrt = new CountDownLatch(1)
        def commitFreigabe = new CountDownLatch(1)
        def pool = Executors.newFixedThreadPool(2)
        try {
            def t1 = pool.submit({
                inTransaction(user) {
                    def result = erster.call()
                    ersterAusgefuehrt.countDown()
                    assert commitFreigabe.await(TIMEOUT, TimeUnit.SECONDS)
                    result
                }
            } as Callable)
            assert ersterAusgefuehrt.await(TIMEOUT, TimeUnit.SECONDS)
            def t2 = pool.submit({ inTransaction(user) { zweiter.call() } } as Callable)
            new PollingConditions(timeout: TIMEOUT).eventually {
                assert wartendAufSperre() == 1
            }
            commitFreigabe.countDown()
            [t1.get(TIMEOUT, TimeUnit.SECONDS), t2.get(TIMEOUT, TimeUnit.SECONDS)]
        } finally {
            commitFreigabe.countDown()
            pool.shutdownNow()
        }
    }

    private Object inTransaction(User user, Closure aufruf) {
        loginAs(user)
        try {
            transaction.execute { aufruf.call() }
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    /** Zahl der Datenbankverbindungen, die gerade auf eine Sperre warten. */
    private int wartendAufSperre() {
        jdbcTemplate.queryForObject(
                "select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'",
                Integer)
    }

    /** Lädt die Listen wie das GET (in eigener Transaktion: die Einträge werden lazy nachgeladen). */
    private List<ShoppingListDto> geladen(User user, Long groupId) {
        inTransaction(user) { shoppingListService.getShoppingListsForGroup(groupId) } as List<ShoppingListDto>
    }

    private List<Long> listIds(Long groupId) {
        shoppingListRepository.findIdsInOrder(groupId)
    }

    private List<Long> itemIds(Long listId) {
        shoppingItemRepository.findIdsInOrder(listId)
    }
}
