package com.github.svenfran.budgetapp.budgetappbackend.entity

import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.hibernate.Hibernate
import org.springframework.beans.factory.annotation.Autowired

/**
 * toString geladener Entities darf keine Datenbankzugriffe auslösen: keine Lazy-Beziehung initialisieren und
 * außerhalb des Persistence Context keine LazyInitializationException werfen.
 *
 * - Bedingungen enthalten nur Strings, Booleans und IDs, keine Entities.
 */
class EntityToStringIntegrationSpec extends IntegrationSpec {

    @Autowired UserRepository userRepository
    @Autowired GroupRepository groupRepository
    @Autowired CartRepository cartRepository

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
}
