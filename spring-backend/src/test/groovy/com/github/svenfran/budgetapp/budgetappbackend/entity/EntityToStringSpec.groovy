package com.github.svenfran.budgetapp.budgetappbackend.entity

import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import spock.lang.Specification

/**
 * toString der Entities: nur eigene Werte und IDs der @ManyToOne-Beziehungen, keine Collections.
 *
 * - Gegenseitige Referenzen (User ↔ Group, Group ↔ Cart) dürfen keinen StackOverflowError auslösen — sonst
 *   verschluckt der Surefire-/Failsafe-Listener einen scheiternden Spock-Vergleich, der eine Entity rendert.
 * - Ob toString Lazy-Beziehungen nachlädt, prüft EntityToStringIntegrationSpec.
 */
class EntityToStringSpec extends Specification {

    def "toString eines Nutzers mit Gruppe, in der er Owner und Mitglied ist, wirft keinen Fehler"() {
        given:
        def anna = TestData.user(id: 1L, name: 'Anna')
        def group = TestData.group(id: 10L, name: 'WG', owner: anna, memberList: [anna] as Set)
        group.addMember(anna)
        anna.groupList.add(group)

        when:
        def text = anna.toString()

        then:
        text == "User{id=1, name='Anna'}"
    }

    def "toString einer Gruppe mit Mitgliedern, Owner und Einkäufen wirft keinen Fehler"() {
        given:
        def anna = TestData.user(id: 1L, name: 'Anna')
        def ben = TestData.user(id: 2L, name: 'Ben')
        def group = TestData.group(id: 10L, name: 'WG', owner: anna, memberList: [anna, ben] as Set)
        group.addMember(anna)
        group.addMember(ben)
        def cart = TestData.cart(id: 100L, group: group, user: ben)
        group.carts.add(cart)
        ben.carts.add(cart)

        when:
        def text = group.toString()

        then:
        text == "Group{id=10, name='WG', dateCreated=null, lastUpdate=null, lastUpdateShoppingList=null, listOrderRevision=0, ownerId=1}"
    }

    def "toString eines Einkaufs, der in den Einkäufen seiner Gruppe steht, wirft keinen Fehler"() {
        given:
        def anna = TestData.user(id: 1L)
        def group = TestData.group(id: 10L, owner: anna)
        group.addMember(anna)
        def category = TestData.category(id: 20L, group: group)
        def template = TestData.cartTemplate(id: 30L)
        def cart = TestData.cart(id: 100L, title: 'Pizza', group: group, user: anna, category: category, template: template)
        group.carts.add(cart)
        anna.carts.add(cart)

        when:
        def text = cart.toString()

        then:
        text.startsWith("Cart{id=100, title='Pizza'")
        text.endsWith(", groupId=10, userId=1, categoryId=20, templateId=30}")
    }

    def "Passwort und E-Mail erscheinen nicht in toString des Nutzers"() {
        given:
        def anna = TestData.user(email: 'anna.geheim@divvy.test', password: 'passwort-hash-4711')

        when:
        def text = anna.toString()

        then:
        !text.contains('anna.geheim')
        !text.contains('passwort-hash-4711')
    }

    def "toString ohne Owner, Template und Kategorie wirft keinen Fehler"() {
        given:
        def group = TestData.group(id: 10L)
        def cart = TestData.cart(id: 100L, group: group, user: TestData.user(id: 1L))

        when:
        def groupText = group.toString()
        def cartText = cart.toString()

        then:
        groupText.endsWith(", ownerId=null}")
        cartText.endsWith(", groupId=10, userId=1, categoryId=null, templateId=null}")
    }

    def "toString von Kategorie, Einkaufsliste und Eintrag gibt Beziehungen nur als ID aus"() {
        given:
        def group = TestData.group(id: 10L, name: 'WG')
        def category = TestData.category(id: 20L, name: 'Lebensmittel', group: group, carts: [] as Set)
        def list = TestData.shoppingList(id: 40L, name: 'Wocheneinkauf', group: group)
        def item = TestData.shoppingItem(id: 50L, name: 'Milch', completed: true, shoppingList: list)
        group.categories.add(category)
        group.shoppingLists.add(list)
        list.shoppingItems.add(item)

        when:
        def categoryText = category.toString()
        def listText = list.toString()
        def itemText = item.toString()

        then:
        categoryText == "Category{id=20, name='Lebensmittel', groupId=10}"
        listText == "ShoppingList{id=40, name='Wocheneinkauf', dateCreated=null, lastUpdate=null, sortOrder=0, itemOrderRevision=0, groupId=10}"
        itemText == "ShoppingItem{id=50, name='Milch', isCompleted=true, dateCreated=null, lastUpdate=null, sortOrder=0, shoppingListId=40}"
    }

    def "toString des Tokens enthält weder Tokenwert noch Geräte-ID"() {
        given:
        def anna = TestData.user(id: 1L)
        def token = TestData.token(id: 60L, token: 'jwt-wert-4711', deviceId: 'geraet-4711', user: anna)
        anna.tokens = [token]

        when:
        def text = token.toString()

        then:
        text == "Token{id=60, tokenType=BEARER, expired=false, revoked=false, userId=1}"
    }
}
