package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*
import com.github.svenfran.budgetapp.budgetappbackend.repository.*
import spock.lang.Specification

class DataLoaderServiceSpec extends Specification {

    // Stubs liefern für Optional-Rückgaben Optional.empty(), d. h. jedes Objekt fehlt.
    def service = new DataLoaderService(Stub(CategoryRepository), Stub(CartRepository), Stub(UserRepository),
            Stub(GroupRepository), Stub(ShoppingListRepository), Stub(ShoppingItemRepository))

    def "Fehlendes Objekt (#methode) wirft #exception.simpleName mit Meldung"() {
        when:
        aufruf(service)

        then:
        def e = thrown(Exception)
        e.class == exception
        e.message == meldung

        where:
        methode            | aufruf                                       || exception                     | meldung
        'loadGroup'        | { it.loadGroup(9L) }                         || GroupNotFoundException        | 'Group with Id 9 not found'
        'loadUserByEmail'  | { it.loadUserByEmail('user@example.com') }   || UserNotFoundException         | 'User with email user@example.com not found'
        'loadCart'         | { it.loadCart(999L) }                        || CartNotFoundException         | 'Cart with Id 999 not found'
        'loadCategory'     | { it.loadCategory(9999L) }                   || CategoryNotFoundException     | 'Category with Id 9999 not found'
        'loadUser'         | { it.loadUser(99L) }                         || UserNotFoundException         | 'User with Id 99 not found'
        'loadShoppingList' | { it.loadShoppingList(99999L) }              || ShoppingListNotFoundException | 'Shoppinglist with Id 99999 not found'
        'loadShoppingItem' | { it.loadShoppingItem(999999L) }             || ShoppingItemNotFoundException | 'Shoppingitem with Id 999999 not found'
    }
}
