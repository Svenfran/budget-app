package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.SettlementPaymentDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartTemplateRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.SettlementRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingPurchaseRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder

import java.time.LocalDate

class UserProfileServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(UserProfileServiceIntegrationSpec)

    @Autowired UserProfileService userProfileService
    @Autowired CartTemplateRepository cartTemplateRepository
    @Autowired GroupRepository groupRepository
    @Autowired ShoppingPurchaseRepository shoppingPurchaseRepository
    @Autowired SettlementService settlementService
    @Autowired SettlementRepository settlementRepository
    @Autowired CartRepository cartRepository

    def "Konto löschen als Owner löst delete-group für die eigene Gruppe mit den aktiven Mitgliedern aus"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def ownerId = wg.owner.id
        def benId = ben.id
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet) löscht sein Konto, Mitglied {}', groupId, ownerId, benId)

        when:
        userProfileService.deleteUserProfile(ownerId)
        LOG.info('userProfileService.deleteUserProfile(Nutzer {}) aufgerufen', ownerId)

        then:
        1 * notificationService.sendGroupDeletedNotification(
                { it*.userId as Set == [ownerId, benId] as Set },
                { it.id == groupId })
        0 * notificationService._
    }

    def "Konto löschen als Owner einer Gruppe mit Ausgleichszahlung löscht Gruppe, Settlement und beide Einkäufe"() {
        given: "DIV-74: cart.settlement_id verweist auf settlement"
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def (groupId, ownerId) = [wg.group.id, wg.owner.id]
        flushAndClear()
        loginAs(wg.owner)
        def carts = settlementService.addSettlementPayment(
                new SettlementPaymentDto(30d, groupId, new UserDto(id: ben.id), DateUtils.toDate(TestData.REFERENCE_DATE)))
        def settlementId = carts[0].settlementId
        flushAndClear()
        LOG.info('Szenario: Nutzer {} (angemeldet) ist Owner von Gruppe {} mit Mitglied {} und Settlement {} (Carts {})',
                ownerId, groupId, ben.id, settlementId, carts*.id)

        when:
        userProfileService.deleteUserProfile(ownerId)
        flushAndClear()
        def gruppeVorhanden = groupRepository.existsById(groupId)
        def settlementVorhanden = settlementRepository.existsById(settlementId)
        def verbliebeneCarts = carts*.id.findAll { cartRepository.existsById(it) }
        LOG.info('userProfileService.deleteUserProfile(Nutzer {}) -> Gruppe vorhanden {}, Settlement vorhanden {}, verbliebene Carts {}',
                ownerId, gruppeVorhanden, settlementVorhanden, verbliebeneCarts)

        then:
        !gruppeVorhanden
        !settlementVorhanden
        verbliebeneCarts.isEmpty()

        cleanup:
        LocaleContextHolder.resetLocaleContext()
    }

    def "Konto löschen als Owner löscht die Einkaufshistorie seiner Gruppe; Gruppen, in denen er Mitglied ist, behalten ihre"() {
        given:
        def owner = fixtures.user()
        def eigeneWg = fixtures.group(owner: owner)
        def andereWg = fixtures.group(members: [owner])
        def eigeneGroupId = eigeneWg.group.id
        def kaufId = fixtures.purchase(fixtures.shoppingList(eigeneWg)).id
        def andererKaufId = fixtures.purchase(fixtures.shoppingList(andereWg)).id
        def ownerId = owner.id
        flushAndClear()
        loginAs(owner)
        LOG.info('Szenario: Nutzer {} (angemeldet) ist Owner von Gruppe {} mit Kauf {} und Mitglied von Gruppe {} mit Kauf {}',
                ownerId, eigeneGroupId, kaufId, andereWg.group.id, andererKaufId)

        when:
        userProfileService.deleteUserProfile(ownerId)
        flushAndClear()
        def gruppeVorhanden = groupRepository.existsById(eigeneGroupId)
        def verbliebeneKaeufe = [kaufId, andererKaufId].findAll { shoppingPurchaseRepository.existsById(it) }
        LOG.info('userProfileService.deleteUserProfile(Nutzer {}) -> Gruppe {} vorhanden {}, verbliebene Käufe {}',
                ownerId, eigeneGroupId, gruppeVorhanden, verbliebeneKaeufe)

        then:
        !gruppeVorhanden
        verbliebeneKaeufe == [andererKaufId]
    }

    def "Konto löschen als Mitglied beendet seine aktiven Templates der Gruppe zum heutigen Tag (testClock)"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def miete = fixtures.cart(wg, user: ben, title: 'Miete')
        def template = fixtures.template(miete)
        testClock.today = LocalDate.of(2025, 7, 1)
        def groupId = wg.group.id
        def benId = ben.id
        def templateId = template.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: heute {}, Gruppe {}, Owner {}, Mitglied {} (angemeldet) löscht sein Konto; aktives Template {} von Ben',
                testClock.today, groupId, wg.owner.id, benId, templateId)

        when:
        userProfileService.deleteUserProfile(benId)
        flushAndClear()
        def gespeichert = cartTemplateRepository.findById(templateId).get()
        LOG.info('userProfileService.deleteUserProfile(Nutzer {}) -> Template {} active {}, endDate {}',
                benId, templateId, gespeichert.active, gespeichert.endDate)

        then:
        !gespeichert.active
        gespeichert.endDate == LocalDate.of(2025, 7, 1)
    }
}
