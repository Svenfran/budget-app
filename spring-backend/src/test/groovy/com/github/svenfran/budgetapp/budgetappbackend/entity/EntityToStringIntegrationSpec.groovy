package com.github.svenfran.budgetapp.budgetappbackend.entity

import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.CategoryRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingItemRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingListRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.TokenRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.hibernate.Hibernate
import org.springframework.beans.factory.annotation.Autowired

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

    @Autowired UserRepository userRepository
    @Autowired GroupRepository groupRepository
    @Autowired CartRepository cartRepository
    @Autowired CategoryRepository categoryRepository
    @Autowired ShoppingListRepository shoppingListRepository
    @Autowired ShoppingItemRepository shoppingItemRepository
    @Autowired TokenRepository tokenRepository

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
             'User.carts'        : Hibernate.isInitialized(loadedUser.carts),
             'User.tokens'       : Hibernate.isInitialized(loadedUser.tokens)]
        }
        def before = lazyState()

        when:
        [loadedCart, loadedGroup, loadedUser]*.toString()
        def after = lazyState()

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

        when: "Entities detachen; der Template-Proxy des Einkaufs ist nicht initialisiert"
        entityManager.clear()
        def cartText = loadedCart.toString()
        def groupText = loadedGroup.toString()
        def userText = loadedUser.toString()

        then:
        noExceptionThrown()
        cartText.endsWith(", templateId=$templateId}")
        groupText.startsWith("Group{id=$groupId, ")
        groupText.endsWith(", ownerId=$ownerId}")
        userText == "User{id=$benId, name='Ben'}"
    }

    def "toString von Kategorie, Einkaufsliste, Eintrag und Token lädt nichts nach und wirft detacht keinen Fehler"() {
        given: "Kategorie mit Einkauf, Einkaufsliste mit Eintrag, Token des Owners"
        def wg = fixtures.group()
        fixtures.cart(wg)
        def list = fixtures.shoppingList(wg)
        def item = fixtures.shoppingItem(list)
        def token = fixtures.token(wg.owner)
        def (groupId, ownerId, categoryId, listId, itemId, tokenId) =
                [wg.group.id, wg.owner.id, wg.category.id, list.id, item.id, token.id]
        flushAndClear()
        def loadedCategory = categoryRepository.findById(categoryId).get()
        def loadedList = shoppingListRepository.findById(listId).get()
        def loadedItem = shoppingItemRepository.findById(itemId).get()
        def loadedToken = tokenRepository.findById(tokenId).get()
        def lazyState = {
            ['Category.carts'            : Hibernate.isInitialized(loadedCategory.carts),
             'ShoppingList.shoppingItems': Hibernate.isInitialized(loadedList.shoppingItems)]
        }
        def before = lazyState()

        when: "toString im Persistence Context, danach detacht"
        [loadedCategory, loadedList, loadedItem, loadedToken]*.toString()
        def after = lazyState()
        entityManager.clear()
        def categoryText = loadedCategory.toString()
        def listText = loadedList.toString()
        def itemText = loadedItem.toString()
        def tokenText = loadedToken.toString()

        then:
        noExceptionThrown()
        before.findAll { it.value } == [:]
        after == before
        categoryText.endsWith(", groupId=$groupId}")
        listText.endsWith(", groupId=$groupId}")
        itemText.endsWith(", shoppingListId=$listId}")
        tokenText.endsWith(", userId=$ownerId}")
    }
}
