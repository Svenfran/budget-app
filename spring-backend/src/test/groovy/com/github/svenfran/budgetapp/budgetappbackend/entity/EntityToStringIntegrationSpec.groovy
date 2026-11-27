package com.github.svenfran.budgetapp.budgetappbackend.entity

import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.SettlementPaymentDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.AuthSessionRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.RefreshTokenRepository
import com.github.svenfran.budgetapp.budgetappbackend.service.AuthSessionService
import com.github.svenfran.budgetapp.budgetappbackend.repository.CategoryRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingItemRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingListRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.service.SettlementService
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.hibernate.Hibernate
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder

/**
 * toString geladener Entities darf keine Datenbankzugriffe auslösen: keine Lazy-Beziehung initialisieren und
 * außerhalb des Persistence Context keine LazyInitializationException werfen.
 *
 * - Bedingungen enthalten nur Strings, Booleans und IDs, keine Entities.
 * - Rollback statt des commit-abhängigen Musters (NOT_SUPPORTED, siehe IntegrationSpec) reicht hier: Nach
 *   flushAndClear() lädt findById frische Entities mit nicht initialisierten Lazy-Beziehungen (jedes Feature prüft
 *   das vorab), und entityManager.clear() detacht sie wie nach dem Ende einer Session.
 */
class EntityToStringIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(EntityToStringIntegrationSpec)

    @Autowired UserRepository userRepository
    @Autowired GroupRepository groupRepository
    @Autowired CartRepository cartRepository
    @Autowired CategoryRepository categoryRepository
    @Autowired ShoppingListRepository shoppingListRepository
    @Autowired ShoppingItemRepository shoppingItemRepository
    @Autowired SettlementService settlementService
    @Autowired AuthSessionService authSessionService
    @Autowired AuthSessionRepository authSessionRepository
    @Autowired RefreshTokenRepository refreshTokenRepository

    def "toString geladener Entities lädt keine Lazy-Beziehungen nach"() {
        given: "Ben ist Mitglied der WG und hat einen wiederkehrenden Einkauf (mit Template)"
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def cart = fixtures.cart(wg, user: ben)
        fixtures.template(cart)
        def (benId, groupId, cartId) = [ben.id, wg.group.id, cart.id]
        flushAndClear()
        def loadedCart = cartRepository.findById(cartId).get()
        def loadedGroup = groupRepository.findById(groupId).get()
        def loadedUser = userRepository.findById(benId).get()
        def lazyState = {
            ['Cart.template'     : Hibernate.isInitialized(loadedCart.template),
             'Group.carts'       : Hibernate.isInitialized(loadedGroup.carts),
             'Group.shoppingLists': Hibernate.isInitialized(loadedGroup.shoppingLists),
             'Group.categories'  : Hibernate.isInitialized(loadedGroup.categories),
             'Group.members'     : Hibernate.isInitialized(loadedGroup.members),
             'Group.memberList'  : Hibernate.isInitialized(loadedGroup.memberList),
             'User.groups'       : Hibernate.isInitialized(loadedUser.groups),
             'User.groupList'    : Hibernate.isInitialized(loadedUser.groupList),
             'User.carts'        : Hibernate.isInitialized(loadedUser.carts)]
        }
        def before = lazyState()
        LOG.info('Szenario: Gruppe {}, Mitglied {}, Einkauf {} mit Template; geladen: Cart, Group, User', groupId, benId, cartId)

        when:
        [loadedCart, loadedGroup, loadedUser]*.toString()
        def after = lazyState()
        LOG.info('toString von Cart {}, Group {}, User {} -> initialisiert vorher {}, nachher {}', cartId, groupId, benId,
                before.findAll { it.value }.keySet(), after.findAll { it.value }.keySet())

        then:
        before.findAll { it.value } == [:]
        after == before
    }

    def "toString detachter Entities wirft keine LazyInitializationException"() {
        given: "Ben ist Mitglied der WG und hat einen wiederkehrenden Einkauf (mit Template)"
        def ben = fixtures.user(name: 'Ben')
        def wg = fixtures.group(members: [ben])
        def cart = fixtures.cart(wg, user: ben)
        def template = fixtures.template(cart)
        def (benId, ownerId, groupId, cartId, templateId) = [ben.id, wg.owner.id, wg.group.id, cart.id, template.id]
        flushAndClear()
        def loadedCart = cartRepository.findById(cartId).get()
        def loadedGroup = groupRepository.findById(groupId).get()
        def loadedUser = userRepository.findById(benId).get()
        LOG.info('Szenario: Gruppe {}, Owner {}, Mitglied {}, Einkauf {} mit Template {}; geladen: Cart, Group, User',
                groupId, ownerId, benId, cartId, templateId)

        when: "Entities detachen; der Template-Proxy des Einkaufs ist nicht initialisiert"
        entityManager.clear()
        def cartText = loadedCart.toString()
        def groupText = loadedGroup.toString()
        def userText = loadedUser.toString()
        LOG.info('toString detacht von Cart {}, Group {}, User {} -> ohne Exception', cartId, groupId, benId)
        LOG.debug('toString: {} / {} / {}', cartText, groupText, userText)

        then:
        noExceptionThrown()
        cartText.endsWith(", templateId=$templateId, settlementId=null}")
        groupText.startsWith("Group{id=$groupId, ")
        groupText.endsWith(", ownerId=$ownerId}")
        userText == "User{id=$benId, name='Ben'}"
    }

    def "toString und CartDto eines Einkaufs mit Ausgleichszahlung lesen nur die Settlement-Id und laden das Settlement nicht nach"() {
        given: "DIV-74: Cart.settlement ist lazy"
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        flushAndClear()
        loginAs(wg.owner)
        def carts = settlementService.addSettlementPayment(
                new SettlementPaymentDto(30d, wg.group.id, new UserDto(id: ben.id), TestData.REFERENCE_DATE))
        def (cartId, settlementId) = [carts[0].id, carts[0].settlementId]
        flushAndClear()
        def loadedCart = cartRepository.findById(cartId).get()
        def vorher = Hibernate.isInitialized(loadedCart.settlement)
        LOG.info('Szenario: Gruppe {}, Owner {}, Mitglied {}; Einkauf {} gehört zu Settlement {}; geladen: Cart', wg.group.id,
                wg.owner.id, ben.id, cartId, settlementId)

        when:
        def text = loadedCart.toString()
        def dtoSettlementId = new CartDto(loadedCart).settlementId
        def nachher = Hibernate.isInitialized(loadedCart.settlement)
        entityManager.clear()
        def detachtText = loadedCart.toString()
        LOG.info('toString/CartDto von Cart {} -> settlementId {}, Settlement initialisiert vorher {}, nachher {}; detacht ohne Exception',
                cartId, dtoSettlementId, vorher, nachher)

        then:
        !vorher
        !nachher
        dtoSettlementId == settlementId
        text.endsWith(", settlementId=$settlementId}")
        detachtText.endsWith(", settlementId=$settlementId}")

        cleanup:
        LocaleContextHolder.resetLocaleContext()
    }

    def "toString von Kategorie, Einkaufsliste und Eintrag lädt nichts nach und wirft detacht keinen Fehler"() {
        given: "Kategorie mit Einkauf, Einkaufsliste mit Eintrag"
        def wg = fixtures.group()
        fixtures.cart(wg)
        def list = fixtures.shoppingList(wg)
        def item = fixtures.shoppingItem(list)
        def (groupId, categoryId, listId, itemId) = [wg.group.id, wg.category.id, list.id, item.id]
        flushAndClear()
        def loadedCategory = categoryRepository.findById(categoryId).get()
        def loadedList = shoppingListRepository.findById(listId).get()
        def loadedItem = shoppingItemRepository.findById(itemId).get()
        def lazyState = {
            ['Category.carts'            : Hibernate.isInitialized(loadedCategory.carts),
             'ShoppingList.shoppingItems': Hibernate.isInitialized(loadedList.shoppingItems)]
        }
        def before = lazyState()
        LOG.info('Szenario: Gruppe {}, Kategorie {} mit Einkauf, Einkaufsliste {} mit Eintrag {}',
                groupId, categoryId, listId, itemId)

        when: "toString im Persistence Context, danach detacht"
        [loadedCategory, loadedList, loadedItem]*.toString()
        def after = lazyState()
        entityManager.clear()
        def categoryText = loadedCategory.toString()
        def listText = loadedList.toString()
        def itemText = loadedItem.toString()
        LOG.info('toString von Category, ShoppingList, ShoppingItem -> initialisiert vorher {}, nachher {}, ' +
                'detacht ohne Exception', before.findAll { it.value }.keySet(), after.findAll { it.value }.keySet())
        LOG.debug('toString: {} / {} / {}', categoryText, listText, itemText)

        then:
        noExceptionThrown()
        before.findAll { it.value } == [:]
        after == before
        categoryText.endsWith(", groupId=$groupId}")
        listText.endsWith(", groupId=$groupId}")
        itemText.endsWith(", shoppingListId=$listId}")
    }

    def "toString von Session und Refresh Token lädt Nutzer, Session und Nachfolger nicht nach und wirft detacht keinen Fehler"() {
        given: "DIV-65: Session mit rotiertem Token (Nachfolger gesetzt); alle @ManyToOne sind lazy"
        def anna = fixtures.user()
        def issued = authSessionService.createSession(anna, 'device-1')
        authSessionService.refresh(issued.refreshToken(), 'device-1')
        def (annaId, sessionId) = [anna.id, issued.sessionId()]
        def (rotatedId, successorId) = refreshTokenRepository.findBySessionIdOrderById(sessionId)*.id
        flushAndClear()
        // einzeln und je in frischem Persistence Context laden: sonst löst Hibernate die Beziehung auf die
        // mitgeladene Entity auf, und sie gilt als initialisiert
        def rotated = refreshTokenRepository.findById(rotatedId).get()
        def lazyState = {
            ['RefreshToken.session'   : Hibernate.isInitialized(rotated.session),
             'RefreshToken.replacedBy': Hibernate.isInitialized(rotated.replacedBy)]
        }
        LOG.info('Szenario: Nutzer {}, Session {}, Token {} rotiert zu {}; geladen: rotierter Token, danach Session',
                annaId, sessionId, rotatedId, successorId)

        when: "toString im Persistence Context, danach detacht – erst Token, dann Session"
        def before = lazyState()
        rotated.toString()
        def afterToken = lazyState()
        entityManager.clear()
        def tokenText = rotated.toString()
        def loadedSession = authSessionRepository.findById(sessionId).get()
        def sessionBefore = Hibernate.isInitialized(loadedSession.user)
        loadedSession.toString()
        def sessionAfter = Hibernate.isInitialized(loadedSession.user)
        entityManager.clear()
        def sessionText = loadedSession.toString()
        def after = afterToken + ['AuthSession.user': sessionAfter]
        before += ['AuthSession.user': sessionBefore]
        LOG.info('toString von RefreshToken {} und AuthSession {} -> initialisiert vorher {}, nachher {}, detacht ohne Exception',
                rotatedId, sessionId, before.findAll { it.value }.keySet(), after.findAll { it.value }.keySet())

        then:
        noExceptionThrown()
        before.findAll { it.value } == [:]
        after == before
        sessionText.endsWith(", userId=$annaId}")
        tokenText.endsWith(", sessionId=$sessionId, replacedById=$successorId}")
    }
}
