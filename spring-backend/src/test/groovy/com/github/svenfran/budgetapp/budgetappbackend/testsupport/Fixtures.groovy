package com.github.svenfran.budgetapp.budgetappbackend.testsupport

import com.github.svenfran.budgetapp.budgetappbackend.constants.TypeEnum
import com.github.svenfran.budgetapp.budgetappbackend.entity.*
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.*
import groovy.transform.TupleConstructor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.time.LocalDate

/**
 * Persistierte, fachlich konsistente Szenarien für Integrations- und API-Specs (Bausteine aus {@link TestData}).
 *
 * - Named Args stehen in Groovy immer im ersten (Map-)Parameter: fixtures.cart(wg, title: 'Pizza').
 * - Methoden entstehen erst mit dem ersten Test, der sie braucht.
 */
@Component
class Fixtures {

    @Autowired UserRepository userRepository
    @Autowired GroupRepository groupRepository
    @Autowired CategoryRepository categoryRepository
    @Autowired CartRepository cartRepository
    @Autowired CartTemplateRepository templateRepository
    @Autowired GroupMembershipHistoryRepository membershipRepository
    @Autowired TokenRepository tokenRepository
    @Autowired ShoppingListRepository shoppingListRepository
    @Autowired ShoppingItemRepository shoppingItemRepository

    User user(Map overrides = [:]) {
        userRepository.save(TestData.user(overrides))
    }

    /** Gruppe mit Owner, Mitgliedern, Mitgliedschaftshistorie und einer Kategorie. Named args: owner, members, memberSince (LocalDate). */
    GroupScenario group(Map args = [:]) {
        User owner = args.owner ?: user()
        List<User> members = args.members ?: []
        LocalDate since = args.memberSince ?: TestData.REFERENCE_DATE.minusYears(1)

        def group = groupRepository.save(TestData.group(owner: owner))
        // Erst die Mitglieder verknüpfen und speichern, danach die Historie schreiben: User.groupList ist eine
        // besitzende @ManyToMany auf group_membership_history; ein späteres Speichern der Nutzer mit leerer
        // groupList könnte die Historienzeilen sonst wieder löschen.
        members.each { group.addMember(it) }
        userRepository.saveAll(members)

        membershipRepository.save(TestData.membership(userId: owner.id, groupId: group.id, type: TypeEnum.OWNER,
                membershipStart: DateUtils.toDate(since)))
        members.each { member ->
            membershipRepository.save(TestData.membership(userId: member.id, groupId: group.id,
                    membershipStart: DateUtils.toDate(since)))
        }
        def category = categoryRepository.save(TestData.category(group: group))
        new GroupScenario(group, owner, members, category)
    }

    /**
     * Zusätzliche Mitgliedschaft eines Nutzers (nur Historie; group.members bleibt unverändert). Named args: Felder von
     * GroupMembershipHistory, z. B. membershipStart/membershipEnd als Date (DateUtils.toDate). Den Nutzer danach nicht
     * erneut speichern: User.groupList bildet dieselbe Tabelle ab (siehe group()).
     */
    GroupMembershipHistory membership(Map overrides = [:], GroupScenario scenario, User user) {
        membershipRepository.save(TestData.membership([userId: user.id, groupId: scenario.group.id] + overrides))
    }

    Category category(Map overrides = [:], GroupScenario scenario) {
        categoryRepository.save(TestData.category([group: scenario.group] + overrides))
    }

    Cart cart(Map overrides = [:], GroupScenario scenario) {
        cartRepository.save(TestData.cart([group: scenario.group, user: scenario.owner, category: scenario.category] + overrides))
    }

    /** Aktives Template zum Cart (Werte aus dem Cart, Start/nächste Ausführung = Kaufdatum) und am Cart verknüpft. */
    CartTemplate template(Map overrides = [:], Cart cart) {
        def purchased = DateUtils.toLocalDate(cart.datePurchased)
        def template = templateRepository.save(TestData.cartTemplate([
                title: cart.title, description: cart.description, amount: cart.amount,
                userId: cart.user.id, groupId: cart.group.id, categoryId: cart.category.id,
                startDate: purchased, nextExecutionDate: purchased] + overrides))
        cart.template = template
        cartRepository.save(cart)
        template
    }

    Token token(Map overrides = [:], User user) {
        tokenRepository.save(TestData.token([user: user] + overrides))
    }

    ShoppingList shoppingList(Map overrides = [:], GroupScenario scenario) {
        shoppingListRepository.save(TestData.shoppingList([group: scenario.group] + overrides))
    }

    ShoppingItem shoppingItem(Map overrides = [:], ShoppingList list) {
        shoppingItemRepository.save(TestData.shoppingItem([shoppingList: list] + overrides))
    }

    @TupleConstructor
    static class GroupScenario {
        Group group
        User owner
        List<User> members
        Category category
    }
}
