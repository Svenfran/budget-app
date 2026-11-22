package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.constants.SupportedCurrency
import com.github.svenfran.budgetapp.budgetappbackend.dto.AddGroupMemberDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.ChangeGroupOwnerDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.RemoveGroupMemberDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.SettlementPaymentDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.CurrencyChangeConfirmationRequiredException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOfGroupException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UnsupportedCurrencyException
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartTemplateRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.CategoryRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupCurrencyChangeRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupMembershipHistoryRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.SettlementRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingItemRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingListRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingPurchaseRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder

import java.time.LocalDate

import static com.github.svenfran.budgetapp.budgetappbackend.constants.TypeEnum.MEMBER
import static com.github.svenfran.budgetapp.budgetappbackend.constants.TypeEnum.OWNER
import static com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData.REFERENCE_DATE

class GroupServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(GroupServiceIntegrationSpec)

    @Autowired GroupService groupService
    @Autowired GroupRepository groupRepository
    @Autowired CartRepository cartRepository
    @Autowired GroupMembershipHistoryRepository membershipRepository
    @Autowired CartTemplateRepository templateRepository
    @Autowired CategoryRepository categoryRepository
    @Autowired ShoppingListRepository shoppingListRepository
    @Autowired ShoppingItemRepository shoppingItemRepository
    @Autowired ShoppingPurchaseRepository shoppingPurchaseRepository
    @Autowired UserRepository userRepository
    @Autowired SettlementService settlementService
    @Autowired SettlementRepository settlementRepository
    @Autowired GroupCurrencyChangeRepository currencyChangeRepository

    // Auslösung der Benachrichtigungen (Konzept 3.8, Ebene 1). Topic, Empfänger und Payload prüft NotificationServiceSpec.

    def "Gruppe umbenennen löst update-group mit dem neuen Namen aus"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet), Mitglied {}; neuer Name "Neue WG"', groupId, wg.owner.id, ben.id)

        when:
        def updated = groupService.updateGroup(new GroupDto(id: groupId, name: 'Neue WG'))
        LOG.info('groupService.updateGroup(Gruppe {}, name "Neue WG") -> name "{}"', groupId, updated.name)

        then:
        1 * notificationService.sendGroupUpdateNotification(groupId, { it.id == groupId && it.name == 'Neue WG' })
        0 * notificationService._
    }

    def "Gruppe umbenennen lässt die Revision der Listenreihenfolge unverändert"() {
        given:
        def wg = fixtures.group()
        def groupId = wg.group.id
        flushAndClear()
        // listOrderRevision ist updatable = false und lässt sich nur per Update-Query setzen
        entityManager.createQuery('update Group g set g.listOrderRevision = 4 where g.id = :groupId')
                .setParameter('groupId', groupId).executeUpdate()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {} mit listOrderRevision 4, Owner {} (angemeldet); neuer Name "Neue WG"', groupId, wg.owner.id)

        when:
        groupService.updateGroup(new GroupDto(id: groupId, name: 'Neue WG'))
        flushAndClear()
        def gespeichert = groupRepository.findById(groupId).get()
        def (name, revision) = [gespeichert.name, gespeichert.listOrderRevision]
        LOG.info('groupService.updateGroup(Gruppe {}, name "Neue WG") -> name "{}", listOrderRevision {}', groupId, name, revision)

        then:
        1 * notificationService.sendGroupUpdateNotification(groupId, _)
        name == 'Neue WG'
        revision == 4L
    }

    def "Mitglied hinzufügen löst add-group-member mit dem neuen Mitglied aus"() {
        given:
        def wg = fixtures.group()
        def ben = fixtures.user()
        def groupId = wg.group.id
        def benId = ben.id
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet), neues Mitglied {}', groupId, wg.owner.id, benId)

        when:
        def result = groupService.addMemberToGroup(new AddGroupMemberDto(id: groupId, newMemberEmail: ben.email))
        LOG.info('groupService.addMemberToGroup(Gruppe {}, Nutzer {}) -> updatedMember {}', groupId, benId, result.updatedMember.id)

        then:
        1 * notificationService.sendGroupMemberAddedNotification(groupId, { it.id == groupId && it.updatedMember.id == benId })
        0 * notificationService._
    }

    def "Mitglied entfernen löst remove-group-member mit der Mitgliederliste von vor der Entfernung aus"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def ownerId = wg.owner.id
        def benId = ben.id
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet) entfernt Mitglied {}', groupId, ownerId, benId)

        when:
        def result = groupService.removeMemberFromGroup(new RemoveGroupMemberDto(id: groupId, member: new UserDto(id: benId)))
        LOG.info('groupService.removeMemberFromGroup(Gruppe {}, Nutzer {}) -> updatedMember {}', groupId, benId, result.updatedMember.id)

        then: "die Liste enthält das entfernte Mitglied, damit dessen Client die Gruppe verwirft"
        1 * notificationService.sendGroupMemberRemovedNotification(
                { it*.userId as Set == [ownerId, benId] as Set },
                { it.id == groupId && it.updatedMember.id == benId })
        0 * notificationService._
    }

    def "Gruppe löschen löst delete-group mit den aktiven Mitgliedern aus"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def ownerId = wg.owner.id
        def benId = ben.id
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet), Mitglied {}', groupId, ownerId, benId)

        when:
        groupService.deleteGroup(groupId)
        LOG.info('groupService.deleteGroup(Gruppe {}) aufgerufen', groupId)

        then:
        1 * notificationService.sendGroupDeletedNotification(
                { it*.userId as Set == [ownerId, benId] as Set },
                { it.id == groupId })
        0 * notificationService._
    }

    def "Ownerwechsel löst change-group-owner mit dem neuen Owner aus"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def benId = ben.id
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet) übergibt an Mitglied {}', groupId, wg.owner.id, benId)

        when:
        groupService.changeGroupOwner(new ChangeGroupOwnerDto(new UserDto(id: benId), groupId))
        LOG.info('groupService.changeGroupOwner(Gruppe {}, neuer Owner {}) aufgerufen', groupId, benId)

        then:
        1 * notificationService.sendGroupOwnerChangedNotification(groupId, { it.id == benId })
        0 * notificationService._
    }

    // ----- addMemberToGroup (DIV-13): Rechte, Historie, Neuberechnung, isDeleted -----
    // Fixtures liefern die Gruppe mit leeren carts/categories/shoppingLists; flushAndClear() vor dem Aufruf, damit der
    // Service die Gruppe neu lädt. Historie mit testClock (GroupMembershipHistoryService nutzt die Clock).

    def "Owner fügt ein Mitglied hinzu: Mitglied der Gruppe mit offener MEMBER-Historie ab heute"() {
        given:
        def wg = fixtures.group()
        def cleo = fixtures.user()
        def groupId = wg.group.id
        def ownerId = wg.owner.id
        def cleoId = cleo.id
        def cleoEmail = cleo.email
        testClock.today = LocalDate.of(2025, 6, 15)
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet, seit {}), neues Mitglied Cleo {}; heute {}', groupId, ownerId,
                REFERENCE_DATE.minusYears(1), cleoId, testClock.today)

        when:
        def result = groupService.addMemberToGroup(new AddGroupMemberDto(id: groupId, newMemberEmail: cleoEmail))
        flushAndClear()
        def mitglieder = memberIds(groupId)
        def cleoHistorie = historie(groupId, cleoId)
        def ownerHistorie = historie(groupId, ownerId)
        LOG.info('groupService.addMemberToGroup(Gruppe {}, Cleo {}) -> members {}; Historie Cleo {}, Owner {}', groupId,
                cleoId, mitglieder, cleoHistorie, ownerHistorie)

        then:
        result.members*.id == [cleoId]
        result.updatedMember.id == cleoId
        mitglieder == [cleoId]
        cleoHistorie == [[MEMBER, LocalDate.of(2025, 6, 15), null]]
        ownerHistorie == [[OWNER, REFERENCE_DATE.minusYears(1), null]]
    }

    def "Hinzufügen lässt die Einkäufe der Gruppe unverändert, keine Neuberechnung von averagePerMember (DIV-39)"() {
        given: "Owner und Ben seit 2024-06-15; beide Einkäufe mit Altwert 99, der eine Neuberechnung sichtbar machen würde"
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def cleo = fixtures.user()
        def groupId = wg.group.id
        def cleoEmail = cleo.email
        def vorEintritt = fixtures.cart(wg, amount: 60d, averagePerMember: 99d, datePurchased: DateUtils.toDate(LocalDate.of(2025, 6, 14)))
        def amEintritt = fixtures.cart(wg, amount: 60d, averagePerMember: 99d, datePurchased: DateUtils.toDate(LocalDate.of(2025, 6, 15)))
        testClock.today = LocalDate.of(2025, 6, 15)
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet), Ben {}, neues Mitglied Cleo {} ab {}; Einkauf {} 60.00 am 2025-06-14, Einkauf {} 60.00 am 2025-06-15',
                groupId, wg.owner.id, ben.id, cleo.id, testClock.today, vorEintritt.id, amEintritt.id)

        when:
        LOG.info('Start: groupService.addMemberToGroup(Gruppe {}, Cleo)', groupId)
        groupService.addMemberToGroup(new AddGroupMemberDto(id: groupId, newMemberEmail: cleoEmail))
        flushAndClear()
        LOG.info('Ende: groupService.addMemberToGroup(Gruppe {}, Cleo) inkl. Flush', groupId)
        def averageVorEintritt = cartRepository.findById(vorEintritt.id).get().averagePerMember
        def averageAmEintritt = cartRepository.findById(amEintritt.id).get().averagePerMember
        LOG.info('groupService.addMemberToGroup(Gruppe {}, Cleo) -> averagePerMember Einkauf {}: {}, Einkauf {}: {}',
                groupId, vorEintritt.id, averageVorEintritt, amEintritt.id, averageAmEintritt)

        then: "Altwerte vor und am Eintrittstag bleiben stehen"
        averageVorEintritt == 99d
        averageAmEintritt == 99d
    }

    def "Wiedereintritt: neuer MEMBER-Eintrag, der alte bleibt beendet, Bens Einkäufe sind wieder sichtbar"() {
        given: "Ben war bis 2025-03-01 Mitglied und steht nicht mehr in group.members; sein Einkauf ist als gelöscht markiert"
        def wg = fixtures.group()
        def ben = fixtures.user()
        def groupId = wg.group.id
        def benId = ben.id
        def benEmail = ben.email
        fixtures.membership(wg, ben, membershipStart: DateUtils.toDate(LocalDate.of(2024, 6, 15)),
                membershipEnd: DateUtils.toDate(LocalDate.of(2025, 3, 1)))
        def benCart = fixtures.cart(wg, user: ben, deleted: true, datePurchased: DateUtils.toDate(LocalDate.of(2025, 2, 1)))
        def ownerCart = fixtures.cart(wg, deleted: true, datePurchased: DateUtils.toDate(LocalDate.of(2025, 2, 1)))
        testClock.today = LocalDate.of(2025, 6, 15)
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet), Ben {} (Mitglied 2024-06-15 bis 2025-03-01); gelöschte Einkäufe Ben {}, Owner {}; heute {}',
                groupId, wg.owner.id, benId, benCart.id, ownerCart.id, testClock.today)

        when:
        groupService.addMemberToGroup(new AddGroupMemberDto(id: groupId, newMemberEmail: benEmail))
        flushAndClear()
        def mitglieder = memberIds(groupId)
        def benHistorie = historie(groupId, benId)
        def benCartDeleted = cartRepository.findById(benCart.id).get().deleted
        def ownerCartDeleted = cartRepository.findById(ownerCart.id).get().deleted
        LOG.info('groupService.addMemberToGroup(Gruppe {}, Ben {}) -> members {}; Historie Ben {}; isDeleted Ben-Einkauf {}, Owner-Einkauf {}',
                groupId, benId, mitglieder, benHistorie, benCartDeleted, ownerCartDeleted)

        then:
        mitglieder == [benId]
        benHistorie == [[MEMBER, LocalDate.of(2024, 6, 15), LocalDate.of(2025, 3, 1)],
                        [MEMBER, LocalDate.of(2025, 6, 15), null]]
        !benCartDeleted
        ownerCartDeleted
    }

    def "#rolle kann kein Mitglied hinzufügen"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def cleo = fixtures.user()
        def angemeldet = rolle == 'Mitglied' ? ben : fixtures.user()
        def groupId = wg.group.id
        def benId = ben.id
        def cleoId = cleo.id
        def cleoEmail = cleo.email
        flushAndClear()
        loginAs(angemeldet)
        LOG.info('Szenario: Gruppe {}, Owner {}, Mitglied Ben {}; angemeldet User {} ({}) fügt Cleo {} hinzu', groupId,
                wg.owner.id, benId, angemeldet.id, rolle, cleoId)

        when:
        LOG.info('groupService.addMemberToGroup(Gruppe {}, Cleo {}) als User {}, erwartet NotOwnerOfGroupException',
                groupId, cleoId, angemeldet.id)
        groupService.addMemberToGroup(new AddGroupMemberDto(id: groupId, newMemberEmail: cleoEmail))

        then:
        thrown(NotOwnerOfGroupException)
        0 * notificationService._
        flushAndClear()
        memberIds(groupId) == [benId]
        historie(groupId, cleoId).isEmpty()

        where:
        rolle << ['Mitglied', 'Außenstehender']
    }

    // ----- removeMemberFromGroup (DIV-13): Rechte, Historie, isDeleted, Templates, Neuberechnung -----
    // Entfernt wird immer ein aktuelles Mitglied, nie der Owner. Bewusst nicht getestet: Owner entfernt sich selbst
    // (DIV-53), Außenstehender „verlässt“ eine fremde Gruppe (DIV-54), Owner entfernt ein Nicht-Mitglied (DIV-55).

    def "#wer entfernt Ben: Bens Historie endet heute, er ist nicht mehr Mitglied"() {
        given: "Owner, Ben und Cleo seit 2024-06-15"
        def ben = fixtures.user()
        def cleo = fixtures.user()
        def wg = fixtures.group(members: [ben, cleo])
        def angemeldet = wer == 'Owner' ? wg.owner : ben
        def groupId = wg.group.id
        def ownerId = wg.owner.id
        def benId = ben.id
        def cleoId = cleo.id
        testClock.today = LocalDate.of(2025, 6, 15)
        flushAndClear()
        loginAs(angemeldet)
        LOG.info('Szenario: Gruppe {}, Owner {}, Ben {}, Cleo {} (alle seit {}); angemeldet User {} ({}); heute {}', groupId,
                ownerId, benId, cleoId, REFERENCE_DATE.minusYears(1), angemeldet.id, wer, testClock.today)

        when:
        def result = groupService.removeMemberFromGroup(new RemoveGroupMemberDto(id: groupId, member: new UserDto(id: benId)))
        flushAndClear()
        def mitglieder = memberIds(groupId)
        def benHistorie = historie(groupId, benId)
        def ownerHistorie = historie(groupId, ownerId)
        def cleoHistorie = historie(groupId, cleoId)
        LOG.info('groupService.removeMemberFromGroup(Gruppe {}, Ben {}) -> members {}; Historie Ben {}, Owner {}, Cleo {}',
                groupId, benId, mitglieder, benHistorie, ownerHistorie, cleoHistorie)

        then:
        result.updatedMember.id == benId
        result.members*.id == [cleoId]
        mitglieder == [cleoId]
        benHistorie == [[MEMBER, REFERENCE_DATE.minusYears(1), LocalDate.of(2025, 6, 15)]]
        ownerHistorie == [[OWNER, REFERENCE_DATE.minusYears(1), null]]
        cleoHistorie == [[MEMBER, REFERENCE_DATE.minusYears(1), null]]

        where: "der Owner entfernt ein Mitglied bzw. Ben verlässt die Gruppe selbst"
        wer << ['Owner', 'Ben selbst']
    }

    def "Entfernen markiert nur Bens Einkäufe in dieser Gruppe als gelöscht"() {
        given: "Ben ist auch Mitglied einer zweiten Gruppe und hat dort einen Einkauf"
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def andereWg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def benId = ben.id
        def benCart = fixtures.cart(wg, user: ben)
        def benCartAndereWg = fixtures.cart(andereWg, user: ben)
        def ownerCart = fixtures.cart(wg)
        testClock.today = LocalDate.of(2025, 6, 15)
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {} (Owner {}) und Gruppe {}, Ben {} in beiden; Einkäufe Ben {} (Gruppe {}), Ben {} (Gruppe {}), Owner {}',
                groupId, wg.owner.id, andereWg.group.id, benId, benCart.id, groupId, benCartAndereWg.id, andereWg.group.id, ownerCart.id)

        when:
        groupService.removeMemberFromGroup(new RemoveGroupMemberDto(id: groupId, member: new UserDto(id: benId)))
        flushAndClear()
        def benCartDeleted = cartRepository.findById(benCart.id).get().deleted
        def benCartAndereWgDeleted = cartRepository.findById(benCartAndereWg.id).get().deleted
        def ownerCartDeleted = cartRepository.findById(ownerCart.id).get().deleted
        LOG.info('groupService.removeMemberFromGroup(Gruppe {}, Ben {}) -> isDeleted Ben {}, Ben andere Gruppe {}, Owner {}',
                groupId, benId, benCartDeleted, benCartAndereWgDeleted, ownerCartDeleted)

        then:
        benCartDeleted
        !benCartAndereWgDeleted
        !ownerCartDeleted
    }

    def "Entfernen deaktiviert nur Bens aktive Templates dieser Gruppe mit Enddatum heute"() {
        given: "Bens aktives und bereits beendetes Template in der Gruppe, Bens Template in einer zweiten Gruppe, Owner-Template"
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def andereWg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def benId = ben.id
        def benAktiv = fixtures.template(fixtures.cart(wg, user: ben))
        def benBeendet = fixtures.template(fixtures.cart(wg, user: ben), active: false, endDate: LocalDate.of(2025, 1, 31))
        def benAndereWg = fixtures.template(fixtures.cart(andereWg, user: ben))
        def ownerAktiv = fixtures.template(fixtures.cart(wg))
        testClock.today = LocalDate.of(2025, 6, 15)
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {} (Owner {}), Ben {}; Templates Ben aktiv {}, Ben beendet {} (Ende 2025-01-31), Ben andere Gruppe {}, Owner {}; heute {}',
                groupId, wg.owner.id, benId, benAktiv.id, benBeendet.id, benAndereWg.id, ownerAktiv.id, testClock.today)

        when:
        groupService.removeMemberFromGroup(new RemoveGroupMemberDto(id: groupId, member: new UserDto(id: benId)))
        flushAndClear()
        def zustand = [benAktiv, benBeendet, benAndereWg, ownerAktiv].collect {
            def template = templateRepository.findById(it.id).get()
            [template.active, template.endDate]
        }
        LOG.info('groupService.removeMemberFromGroup(Gruppe {}, Ben {}) -> [active, endDate] Ben aktiv {}, Ben beendet {}, Ben andere Gruppe {}, Owner {}',
                groupId, benId, *zustand)

        then:
        zustand == [[false, LocalDate.of(2025, 6, 15)],
                    [false, LocalDate.of(2025, 1, 31)],
                    [true, null],
                    [true, null]]
    }

    def "Entfernen lässt die Einkäufe der Gruppe unverändert, keine Neuberechnung von averagePerMember (DIV-39)"() {
        given: "Owner und Ben seit 2024-06-15; Einkäufe des Owners mit Altwert 99, der eine Neuberechnung sichtbar machen würde"
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def benId = ben.id
        // Ein Kaufdatum nach heute ist zulässig, solange die Mitgliedschaft des Käufers offen ist.
        def amAustritt = fixtures.cart(wg, amount: 60d, averagePerMember: 99d, datePurchased: DateUtils.toDate(LocalDate.of(2025, 6, 15)))
        def nachAustritt = fixtures.cart(wg, amount: 60d, averagePerMember: 99d, datePurchased: DateUtils.toDate(LocalDate.of(2025, 6, 16)))
        testClock.today = LocalDate.of(2025, 6, 15)
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet), Ben {} wird am {} entfernt; Einkauf {} 60.00 am 2025-06-15, Einkauf {} 60.00 am 2025-06-16',
                groupId, wg.owner.id, benId, testClock.today, amAustritt.id, nachAustritt.id)

        when:
        LOG.info('Start: groupService.removeMemberFromGroup(Gruppe {}, Ben {})', groupId, benId)
        groupService.removeMemberFromGroup(new RemoveGroupMemberDto(id: groupId, member: new UserDto(id: benId)))
        flushAndClear()
        LOG.info('Ende: groupService.removeMemberFromGroup(Gruppe {}, Ben {}) inkl. Flush', groupId, benId)
        def averageAmAustritt = cartRepository.findById(amAustritt.id).get().averagePerMember
        def averageNachAustritt = cartRepository.findById(nachAustritt.id).get().averagePerMember
        LOG.info('groupService.removeMemberFromGroup(Gruppe {}, Ben {}) -> averagePerMember Einkauf {}: {}, Einkauf {}: {}',
                groupId, benId, amAustritt.id, averageAmAustritt, nachAustritt.id, averageNachAustritt)

        then: "Altwerte am und nach dem Austrittstag bleiben stehen"
        averageAmAustritt == 99d
        averageNachAustritt == 99d
    }

    def "#rolle kann Ben nicht entfernen"() {
        given: "Ben hat einen Einkauf mit aktivem Template"
        def ben = fixtures.user()
        def cleo = fixtures.user()
        def wg = fixtures.group(members: [ben, cleo])
        def angemeldet = rolle == 'Mitglied' ? cleo : fixtures.user()
        def groupId = wg.group.id
        def benId = ben.id
        def cleoId = cleo.id
        def benCart = fixtures.cart(wg, user: ben)
        def benTemplate = fixtures.template(benCart)
        flushAndClear()
        loginAs(angemeldet)
        LOG.info('Szenario: Gruppe {}, Owner {}, Ben {} (Einkauf {}, Template {}), Cleo {}; angemeldet User {} ({})', groupId,
                wg.owner.id, benId, benCart.id, benTemplate.id, cleoId, angemeldet.id, rolle)

        when:
        LOG.info('groupService.removeMemberFromGroup(Gruppe {}, Ben {}) als User {}, erwartet NotOwnerOfGroupException',
                groupId, benId, angemeldet.id)
        groupService.removeMemberFromGroup(new RemoveGroupMemberDto(id: groupId, member: new UserDto(id: benId)))

        then:
        thrown(NotOwnerOfGroupException)
        0 * notificationService._
        flushAndClear()
        memberIds(groupId) == [benId, cleoId].sort()
        historie(groupId, benId) == [[MEMBER, REFERENCE_DATE.minusYears(1), null]]
        !cartRepository.findById(benCart.id).get().deleted
        templateRepository.findById(benTemplate.id).get().active

        where: "Cleo ist Mitglied, darf aber nur sich selbst entfernen"
        rolle << ['Mitglied', 'Außenstehender']
    }

    // ----- changeGroupOwner (DIV-13): Rechte, Rollentausch in Gruppe und Historie -----
    // Neuer Owner ist immer ein aktuelles Mitglied. Bewusst nicht getestet: Übergabe an Nicht-Mitglieder oder an sich
    // selbst (DIV-52).

    def "Ownerwechsel tauscht die Rollen in Gruppe und Historie"() {
        given: "Anna ist Owner, Ben Mitglied, beide seit 2024-06-15"
        def anna = fixtures.user()
        def ben = fixtures.user()
        def wg = fixtures.group(owner: anna, members: [ben])
        def groupId = wg.group.id
        def annaId = anna.id
        def benId = ben.id
        testClock.today = LocalDate.of(2025, 6, 15)
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Gruppe {}, Owner Anna {} (angemeldet), Mitglied Ben {} (beide seit {}); heute {}', groupId, annaId,
                benId, REFERENCE_DATE.minusYears(1), testClock.today)

        when:
        groupService.changeGroupOwner(new ChangeGroupOwnerDto(new UserDto(id: benId), groupId))
        flushAndClear()
        def ownerId = groupRepository.findById(groupId).get().owner.id
        def mitglieder = memberIds(groupId)
        def annaHistorie = historie(groupId, annaId)
        def benHistorie = historie(groupId, benId)
        def offen = membershipRepository.findByGroupIdAndMembershipEndIsNull(groupId)*.userId.sort()
        LOG.info('groupService.changeGroupOwner(Gruppe {}, neuer Owner Ben {}) -> owner {}, members {}; Historie Anna {}, Ben {}',
                groupId, benId, ownerId, mitglieder, annaHistorie, benHistorie)

        then:
        ownerId == benId
        mitglieder == [annaId]
        annaHistorie == [[OWNER, REFERENCE_DATE.minusYears(1), LocalDate.of(2025, 6, 15)],
                         [MEMBER, LocalDate.of(2025, 6, 15), null]]
        benHistorie == [[MEMBER, REFERENCE_DATE.minusYears(1), LocalDate.of(2025, 6, 15)],
                        [OWNER, LocalDate.of(2025, 6, 15), null]]
        offen == [annaId, benId].sort()
    }

    def "#rolle kann den Owner nicht wechseln"() {
        given:
        def ben = fixtures.user()
        def cleo = fixtures.user()
        def wg = fixtures.group(members: [ben, cleo])
        def angemeldet = rolle == 'Mitglied' ? ben : fixtures.user()
        def groupId = wg.group.id
        def ownerId = wg.owner.id
        def benId = ben.id
        def cleoId = cleo.id
        flushAndClear()
        loginAs(angemeldet)
        LOG.info('Szenario: Gruppe {}, Owner {}, Ben {}, Cleo {}; angemeldet User {} ({}) übergibt an Cleo', groupId, ownerId,
                benId, cleoId, angemeldet.id, rolle)

        when:
        LOG.info('groupService.changeGroupOwner(Gruppe {}, neuer Owner Cleo {}) als User {}, erwartet NotOwnerOfGroupException',
                groupId, cleoId, angemeldet.id)
        groupService.changeGroupOwner(new ChangeGroupOwnerDto(new UserDto(id: cleoId), groupId))

        then:
        thrown(NotOwnerOfGroupException)
        0 * notificationService._
        flushAndClear()
        groupRepository.findById(groupId).get().owner.id == ownerId
        memberIds(groupId) == [benId, cleoId].sort()
        historie(groupId, ownerId) == [[OWNER, REFERENCE_DATE.minusYears(1), null]]
        historie(groupId, cleoId) == [[MEMBER, REFERENCE_DATE.minusYears(1), null]]

        where: "Ben ist Mitglied der Gruppe, der Außenstehende nicht"
        rolle << ['Mitglied', 'Außenstehender']
    }

    // ----- deleteGroup (DIV-13): Rechte, vollständige Löschung -----

    def "Owner löscht die Gruppe mit allen abhängigen Daten, andere Gruppen bleiben unverändert"() {
        given: "Gruppe mit Ben, Kategorie, Einkauf mit Template, Einkaufsliste mit Eintrag und beendeter Mitgliedschaft; Ben ist auch in einer zweiten Gruppe"
        def ben = fixtures.user()
        def dora = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def andereWg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def andereGroupId = andereWg.group.id
        def ownerId = wg.owner.id
        def benId = ben.id
        fixtures.membership(wg, dora, membershipStart: DateUtils.toDate(LocalDate.of(2024, 6, 15)),
                membershipEnd: DateUtils.toDate(LocalDate.of(2025, 3, 1)))
        def benCart = fixtures.cart(wg, user: ben)
        def template = fixtures.template(benCart)
        def liste = fixtures.shoppingList(wg)
        def eintrag = fixtures.shoppingItem(liste)
        def andererCart = fixtures.cart(andereWg, user: ben)
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {} (Owner {} angemeldet, Ben {}, Ex-Mitglied Dora {}; Kategorie {}, Einkauf {} mit Template {}, Liste {} mit Eintrag {}); Gruppe {} mit Ben und Einkauf {}',
                groupId, ownerId, benId, dora.id, wg.category.id, benCart.id, template.id, liste.id, eintrag.id, andereGroupId, andererCart.id)

        when:
        groupService.deleteGroup(groupId)
        flushAndClear()
        def gruppeVorhanden = groupRepository.findById(groupId).present
        def historieDerGruppe = membershipRepository.findByGroupId(groupId)
        def carts = cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(groupId)
        def kategorien = categoryRepository.findAllByGroup_IdOrderByName(groupId)
        def templates = templateRepository.findByGroupId(groupId)
        def listen = shoppingListRepository.findAllByGroup_IdOrderBySortOrderAscIdAsc(groupId)
        def eintragVorhanden = shoppingItemRepository.findById(eintrag.id).present
        LOG.info('groupService.deleteGroup(Gruppe {}) -> Gruppe vorhanden {}; Historie {}, Einkäufe {}, Kategorien {}, Templates {}, Listen {}, Eintrag vorhanden {}',
                groupId, gruppeVorhanden, historieDerGruppe.size(), carts.size(), kategorien.size(), templates.size(), listen.size(), eintragVorhanden)

        then:
        !gruppeVorhanden
        historieDerGruppe.isEmpty()
        carts.isEmpty()
        kategorien.isEmpty()
        templates.isEmpty()
        listen.isEmpty()
        !eintragVorhanden

        and: "Nutzer und die zweite Gruppe bleiben unverändert"
        userRepository.findById(ownerId).present
        userRepository.findById(benId).present
        memberIds(andereGroupId) == [benId]
        historie(andereGroupId, benId) == [[MEMBER, REFERENCE_DATE.minusYears(1), null]]
        cartRepository.findById(andererCart.id).present
    }

    def "Gruppe mit Ausgleichszahlung löschen entfernt Settlement und beide Einkäufe; die Ausgleichszahlung einer anderen Gruppe bleibt"() {
        given: "DIV-74: cart.settlement_id verweist auf settlement, die Gruppe muss sich trotzdem löschen lassen"
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def andereWg = fixtures.group(owner: wg.owner, members: [ben])
        def (groupId, andereGroupId) = [wg.group.id, andereWg.group.id]
        flushAndClear()
        loginAs(wg.owner)
        def carts = settlementService.addSettlementPayment(
                new SettlementPaymentDto(30d, groupId, new UserDto(id: ben.id), REFERENCE_DATE))
        def andereCarts = settlementService.addSettlementPayment(
                new SettlementPaymentDto(20d, andereGroupId, new UserDto(id: ben.id), REFERENCE_DATE))
        def (settlementId, andereSettlementId) = [carts[0].settlementId, andereCarts[0].settlementId]
        flushAndClear()
        LOG.info('Szenario: Gruppe {} (Owner {} angemeldet, Ben {}) mit Settlement {} (Carts {}); Gruppe {} mit Settlement {} (Carts {})',
                groupId, wg.owner.id, ben.id, settlementId, carts*.id, andereGroupId, andereSettlementId, andereCarts*.id)

        when:
        groupService.deleteGroup(groupId)
        flushAndClear()
        def settlementVorhanden = settlementRepository.existsById(settlementId)
        def verbliebeneCarts = (carts + andereCarts)*.id.findAll { cartRepository.existsById(it) }
        def andereSettlementVorhanden = settlementRepository.existsById(andereSettlementId)
        LOG.info('groupService.deleteGroup(Gruppe {}) -> Settlement {} vorhanden {}, anderes Settlement vorhanden {}, verbliebene Carts {}',
                groupId, settlementId, settlementVorhanden, andereSettlementVorhanden, verbliebeneCarts)

        then:
        !groupRepository.existsById(groupId)
        !settlementVorhanden
        verbliebeneCarts == andereCarts*.id
        andereSettlementVorhanden

        cleanup:
        LocaleContextHolder.resetLocaleContext()
    }

    def "Standardgruppe enthält genau eine gekennzeichnete Settlement-Kategorie (Locale #locale)"() {
        given:
        LocaleContextHolder.setLocale(locale)
        def anna = fixtures.user()
        flushAndClear()
        LOG.info('Szenario: neuer Nutzer {}, Locale {}', anna.id, locale)

        when:
        groupService.createDefaultGroup(anna)
        flushAndClear()
        def groupId = groupRepository.findGroupsByOwnerOrderById(userRepository.findById(anna.id).get()).first().id
        def kategorien = categoryRepository.findAllByGroup_IdOrderByName(groupId)
        LOG.info('groupService.createDefaultGroup(Nutzer {}) -> Gruppe {}, Kategorien {}, gekennzeichnet {}', anna.id, groupId,
                kategorien*.name, kategorien.findAll { it.settlement }*.name)

        then:
        kategorien.size() == 7
        kategorien.findAll { it.settlement }*.name == [name]

        cleanup:
        LocaleContextHolder.resetLocaleContext()

        where:
        locale         || name
        Locale.GERMAN  || 'Ausgleichszahlung'
        Locale.ENGLISH || 'Repayment'
    }

    def "Gruppe löschen entfernt ihre Einkaufshistorie, auch die gelöschter Listen; die Historie anderer Gruppen bleibt"() {
        given:
        def wg = fixtures.group()
        def andereWg = fixtures.group()
        def liste = fixtures.shoppingList(wg)
        def andereListe = fixtures.shoppingList(andereWg)
        def groupId = wg.group.id
        def kaufId = fixtures.purchase(liste).id
        def verwaistId = fixtures.purchase(liste, shoppingListId: -1L).id
        def andererKaufId = fixtures.purchase(andereListe).id
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {} (Owner {} angemeldet) mit Kauf {} der Liste {} und verwaistem Kauf {} (gelöschte Liste); Gruppe {} mit Kauf {}',
                groupId, wg.owner.id, kaufId, liste.id, verwaistId, andereWg.group.id, andererKaufId)

        when:
        groupService.deleteGroup(groupId)
        flushAndClear()
        def verbliebeneKaeufe = [kaufId, verwaistId, andererKaufId].findAll { shoppingPurchaseRepository.existsById(it) }
        LOG.info('groupService.deleteGroup(Gruppe {}) -> verbliebene Käufe {}', groupId, verbliebeneKaeufe)

        then:
        !groupRepository.existsById(groupId)
        verbliebeneKaeufe == [andererKaufId]
    }

    def "#rolle kann die Gruppe nicht löschen"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def angemeldet = rolle == 'Mitglied' ? ben : fixtures.user()
        def groupId = wg.group.id
        def benId = ben.id
        def cart = fixtures.cart(wg, user: ben)
        flushAndClear()
        loginAs(angemeldet)
        LOG.info('Szenario: Gruppe {}, Owner {}, Ben {} (Einkauf {}); angemeldet User {} ({})', groupId, wg.owner.id, benId,
                cart.id, angemeldet.id, rolle)

        when:
        LOG.info('groupService.deleteGroup(Gruppe {}) als User {}, erwartet NotOwnerOfGroupException', groupId, angemeldet.id)
        groupService.deleteGroup(groupId)

        then:
        thrown(NotOwnerOfGroupException)
        0 * notificationService._
        flushAndClear()
        memberIds(groupId) == [benId]
        membershipRepository.findByGroupId(groupId).size() == 2
        cartRepository.findById(cart.id).present

        where:
        rolle << ['Mitglied', 'Außenstehender']
    }

    // ----- Gruppenwährung (DIV-77) -----

    def "Gruppe anlegen mit #gesendet speichert #erwartet"() {
        given:
        def anna = fixtures.user()
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Nutzer {} (angemeldet) legt Gruppe "Reise" an, currencyCode {}', anna.id, gesendet)

        when:
        def dto = groupService.addGroup(new GroupDto(name: 'Reise', currencyCode: gesendet))
        flushAndClear()
        def gespeichert = groupRepository.findById(dto.id).get().currencyCode
        LOG.info('groupService.addGroup(currencyCode {}) -> Gruppe {}, Antwort {}, gespeichert {}', gesendet, dto.id, dto.currencyCode, gespeichert)

        then:
        dto.currencyCode == erwartet.name()
        gespeichert == erwartet

        where:
        gesendet || erwartet
        'USD'    || SupportedCurrency.USD
        'CZK'    || SupportedCurrency.CZK
        'NOK'    || SupportedCurrency.NOK
        'SEK'    || SupportedCurrency.SEK
        null     || SupportedCurrency.EUR
    }

    def "Gruppe anlegen mit nicht unterstützter Währung wird abgelehnt"() {
        given:
        def anna = fixtures.user()
        def annaId = anna.id
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Nutzer {} (angemeldet) legt Gruppe "Reise" mit JPY an', annaId)

        when:
        LOG.info('groupService.addGroup(currencyCode JPY), erwartet UnsupportedCurrencyException')
        groupService.addGroup(new GroupDto(name: 'Reise', currencyCode: 'JPY'))

        then:
        thrown(UnsupportedCurrencyException)
        flushAndClear()
        groupRepository.findGroupsByOwnerOrderById(userRepository.findById(annaId).get()).isEmpty()
    }

    def "Standardgruppe bei der Registrierung hat EUR"() {
        given:
        def anna = fixtures.user()
        flushAndClear()
        LOG.info('Szenario: neuer Nutzer {}', anna.id)

        when:
        groupService.createDefaultGroup(anna)
        flushAndClear()
        def waehrung = groupRepository.findGroupsByOwnerOrderById(userRepository.findById(anna.id).get()).first().currencyCode
        LOG.info('groupService.createDefaultGroup(Nutzer {}) -> Währung {}', anna.id, waehrung)

        then:
        waehrung == SupportedCurrency.EUR
    }

    def "Währungswechsel einer Gruppe ohne Einkäufe braucht keine Bestätigung, wird protokolliert und synchronisiert"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def (groupId, ownerId) = [wg.group.id, wg.owner.id]
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {} (EUR, ohne Einkäufe), Owner {} (angemeldet), Mitglied {}; heute {}; Wechsel auf USD ohne Bestätigung',
                groupId, ownerId, ben.id, testClock.today)

        when:
        def dto = groupService.updateGroup(new GroupDto(id: groupId, name: 'WG', currencyCode: 'USD'))
        flushAndClear()
        def gespeichert = groupRepository.findById(groupId).get().currencyCode
        def protokoll = protokoll(groupId)
        LOG.info('groupService.updateGroup(Gruppe {}, USD) -> Antwort {}, gespeichert {}, Protokoll {}', groupId, dto.currencyCode, gespeichert, protokoll)

        then:
        1 * notificationService.sendGroupUpdateNotification(groupId, { it.id == groupId && it.currencyCode == 'USD' })
        0 * notificationService._
        dto.currencyCode == 'USD'
        gespeichert == SupportedCurrency.USD
        protokoll == [['EUR', 'USD', ownerId, REFERENCE_DATE]]
    }

    def "Währungswechsel ohne Bestätigung bei #buchung wird abgelehnt und ändert nichts"() {
        given:
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def alterName = wg.group.name
        flushAndClear()
        loginAs(wg.owner)
        if (buchung == 'Ausgabe') fixtures.cart(wg)
        if (buchung == 'Ausgleichszahlung') settlementService.addSettlementPayment(
                new SettlementPaymentDto(30d, groupId, new UserDto(id: ben.id), REFERENCE_DATE))
        if (buchung == 'nur gelöschten Einkäufen') fixtures.cart(wg, user: ben, deleted: true)
        flushAndClear()
        LOG.info('Szenario: Gruppe {} (EUR) mit {}, Owner {} (angemeldet); Wechsel auf USD mit neuem Namen, confirmCurrencyChange {}',
                groupId, buchung, wg.owner.id, bestaetigung)

        when:
        LOG.info('groupService.updateGroup(Gruppe {}, USD, confirm {}), erwartet CurrencyChangeConfirmationRequiredException', groupId, bestaetigung)
        groupService.updateGroup(new GroupDto(id: groupId, name: 'Neu', currencyCode: 'USD', confirmCurrencyChange: bestaetigung))

        then:
        thrown(CurrencyChangeConfirmationRequiredException)
        0 * notificationService._
        flushAndClear()
        def gruppe = groupRepository.findById(groupId).get()
        gruppe.currencyCode == SupportedCurrency.EUR
        gruppe.name == alterName
        protokoll(groupId).isEmpty()

        cleanup:
        LocaleContextHolder.resetLocaleContext()

        where:
        buchung                    | bestaetigung
        'Ausgabe'                  | null
        'Ausgabe'                  | false
        'Ausgleichszahlung'        | null
        'nur gelöschten Einkäufen' | null
    }

    def "Gruppe nur mit Vorlage ohne Einkauf wechselt die Währung ohne Bestätigung"() {
        given: "Altdaten: aktive Vorlage, aber kein Cart (D5: Vorlagen zählen nicht als Buchung)"
        def wg = fixtures.group()
        def groupId = wg.group.id
        def vorlage = templateRepository.save(TestData.cartTemplate(userId: wg.owner.id, groupId: groupId, categoryId: wg.category.id))
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {} (EUR) nur mit Vorlage {}, Owner {} (angemeldet); Wechsel auf CHF ohne Bestätigung',
                groupId, vorlage.id, wg.owner.id)

        when:
        def dto = groupService.updateGroup(new GroupDto(id: groupId, name: 'WG', currencyCode: 'CHF'))
        LOG.info('groupService.updateGroup(Gruppe {}, CHF) -> {}', groupId, dto.currencyCode)

        then:
        dto.currencyCode == 'CHF'
    }

    def "Bestätigter Währungswechsel ändert keine Beträge von Einkäufen, Vorlagen und Ausgleichszahlungen"() {
        given:
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def (groupId, ownerId) = [wg.group.id, wg.owner.id]
        def einkauf = fixtures.cart(wg, amount: 65.349875d)
        def vorlage = fixtures.template(einkauf)
        flushAndClear()
        loginAs(wg.owner)
        def zahlung = settlementService.addSettlementPayment(
                new SettlementPaymentDto(30.5d, groupId, new UserDto(id: ben.id), REFERENCE_DATE))
        flushAndClear()
        def settlementId = zahlung[0].settlementId
        def cartIds = [einkauf.id] + zahlung*.id
        def vorher = [cartIds.collect { cartRepository.findById(it).get().amount },
                      templateRepository.findById(vorlage.id).get().amount,
                      settlementRepository.findById(settlementId).get().amount]
        flushAndClear()
        LOG.info('Szenario: Gruppe {} (EUR), Owner {} (angemeldet); Einkäufe {}, Vorlage {}, Settlement {}; Beträge vorher {}; Wechsel auf GBP mit Bestätigung',
                groupId, ownerId, cartIds, vorlage.id, settlementId, vorher)

        when:
        def dto = groupService.updateGroup(new GroupDto(id: groupId, name: 'WG', currencyCode: 'GBP', confirmCurrencyChange: true))
        flushAndClear()
        def nachher = [cartIds.collect { cartRepository.findById(it).get().amount },
                       templateRepository.findById(vorlage.id).get().amount,
                       settlementRepository.findById(settlementId).get().amount]
        def protokoll = protokoll(groupId)
        LOG.info('groupService.updateGroup(Gruppe {}, GBP, bestätigt) -> {}; Beträge nachher {}, Protokoll {}', groupId, dto.currencyCode, nachher, protokoll)

        then:
        1 * notificationService.sendGroupUpdateNotification(groupId, { it.currencyCode == 'GBP' })
        dto.currencyCode == 'GBP'
        nachher == vorher
        nachher[1] == 65.349875d
        nachher[2] == 30.5d
        protokoll == [['EUR', 'GBP', ownerId, REFERENCE_DATE]]

        cleanup:
        LocaleContextHolder.resetLocaleContext()
    }

    def "Umbenennen #fall lässt die Währung unverändert und schreibt kein Protokoll"() {
        given:
        def wg = fixtures.group()
        def groupId = wg.group.id
        flushAndClear()
        entityManager.createQuery('update Group g set g.currencyCode = :c where g.id = :id')
                .setParameter('c', SupportedCurrency.USD).setParameter('id', groupId).executeUpdate()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {} (USD), Owner {} (angemeldet); Rename auf "Neu" mit currencyCode {}', groupId, wg.owner.id, gesendet)

        when:
        def dto = groupService.updateGroup(new GroupDto(id: groupId, name: 'Neu', currencyCode: gesendet))
        flushAndClear()
        def gruppe = groupRepository.findById(groupId).get()
        def (name, waehrung) = [gruppe.name, gruppe.currencyCode]
        LOG.info('groupService.updateGroup(Gruppe {}, "Neu", {}) -> Antwort {}, gespeichert "{}" {}', groupId, gesendet, dto.currencyCode, name, waehrung)

        then:
        1 * notificationService.sendGroupUpdateNotification(groupId, { it.name == 'Neu' && it.currencyCode == 'USD' })
        dto.currencyCode == 'USD'
        name == 'Neu'
        waehrung == SupportedCurrency.USD
        protokoll(groupId).isEmpty()

        where:
        fall                                        | gesendet
        'ohne currencyCode (alte App-Version)'      | null
        'mit derselben Währung'                     | 'USD'
    }

    def "Umbenennen lässt lastUpdateShoppingList unverändert"() {
        given: "Regression: updateGroup speicherte früher eine neu gebaute Gruppe per Merge und überschrieb das Feld mit null"
        def wg = fixtures.group()
        def groupId = wg.group.id
        def stand = DateUtils.toDate(LocalDate.of(2025, 5, 1))
        wg.group.lastUpdateShoppingList = stand
        groupRepository.save(wg.group)
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {} mit lastUpdateShoppingList {}, Owner {} (angemeldet); Rename auf "Neu"', groupId, stand, wg.owner.id)

        when:
        groupService.updateGroup(new GroupDto(id: groupId, name: 'Neu'))
        flushAndClear()
        def gespeichert = groupRepository.findById(groupId).get().lastUpdateShoppingList
        LOG.info('groupService.updateGroup(Gruppe {}, "Neu") -> lastUpdateShoppingList {}', groupId, gespeichert)

        then:
        DateUtils.toLocalDate(gespeichert) == LocalDate.of(2025, 5, 1)
    }

    def "Mitglied kann die Währung nicht ändern"() {
        given:
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        flushAndClear()
        loginAs(ben)
        LOG.info('Szenario: Gruppe {} (EUR, ohne Einkäufe), Owner {}, Mitglied {} (angemeldet); Wechsel auf USD', groupId, wg.owner.id, ben.id)

        when:
        LOG.info('groupService.updateGroup(Gruppe {}, USD) als Mitglied {}, erwartet NotOwnerOfGroupException', groupId, ben.id)
        groupService.updateGroup(new GroupDto(id: groupId, name: 'Neu', currencyCode: 'USD'))

        then:
        thrown(NotOwnerOfGroupException)
        0 * notificationService._
        flushAndClear()
        groupRepository.findById(groupId).get().currencyCode == SupportedCurrency.EUR
        protokoll(groupId).isEmpty()
    }

    def "Gruppe mit protokollierten Währungswechseln löschen entfernt auch das Protokoll"() {
        given:
        def wg = fixtures.group()
        def groupId = wg.group.id
        flushAndClear()
        loginAs(wg.owner)
        groupService.updateGroup(new GroupDto(id: groupId, name: 'WG', currencyCode: 'USD'))
        groupService.updateGroup(new GroupDto(id: groupId, name: 'WG', currencyCode: 'CHF'))
        flushAndClear()
        def eintraege = protokoll(groupId).size()
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet), {} Protokolleinträge', groupId, wg.owner.id, eintraege)

        when:
        groupService.deleteGroup(groupId)
        flushAndClear()
        def gruppeVorhanden = groupRepository.existsById(groupId)
        def verbleibend = currencyChangeRepository.findByGroupIdOrderByChangedAtAscIdAsc(groupId).size()
        LOG.info('groupService.deleteGroup(Gruppe {}) -> Gruppe vorhanden {}, verbleibende Protokolleinträge {}', groupId, gruppeVorhanden, verbleibend)

        then:
        eintraege == 2
        !gruppeVorhanden
        verbleibend == 0
    }

    def "Übersicht meldet hasBookings je Gruppe"() {
        given:
        def anna = fixtures.user()
        def mitEinkauf = fixtures.group(owner: anna)
        def mitGeloeschtem = fixtures.group(owner: anna)
        def leer = fixtures.group(owner: anna)
        fixtures.cart(mitEinkauf)
        fixtures.cart(mitGeloeschtem, deleted: true)
        def ids = [mitEinkauf, mitGeloeschtem, leer]*.group*.id
        flushAndClear()
        loginAs(anna)
        LOG.info('Szenario: Owner {} (angemeldet); Gruppe {} mit Einkauf, Gruppe {} nur mit gelöschtem Einkauf, Gruppe {} leer', anna.id, *ids)

        when:
        def overview = groupService.getGroupsForGroupOverview()
        def hasBookings = ids.collect { id -> overview.find { it.id == id }.hasBookings }
        LOG.info('groupService.getGroupsForGroupOverview() -> hasBookings {}', hasBookings)

        then:
        hasBookings == [true, true, false]
    }

    /** Protokoll der Währungswechsel als [alt, neu, Nutzer-ID, Tag], in Reihenfolge. */
    private List<List> protokoll(Long groupId) {
        currencyChangeRepository.findByGroupIdOrderByChangedAtAscIdAsc(groupId)
                .collect { [it.oldCurrencyCode.name(), it.newCurrencyCode.name(), it.changedByUserId, DateUtils.toLocalDate(it.changedAt)] }
    }

    /** Mitglieder-IDs der Gruppe (ohne Owner), aus der Datenbank geladen. */
    private List<Long> memberIds(Long groupId) {
        groupRepository.findById(groupId).get().members*.id.sort()
    }

    /** Historie eines Nutzers in der Gruppe als [Typ, Start, Ende], sortiert nach Start und ID. */
    private List<List> historie(Long groupId, Long userId) {
        membershipRepository.findByGroupIdAndUserId(groupId, userId)
                .sort { a, b -> a.membershipStart <=> b.membershipStart ?: a.id <=> b.id }
                .collect { [it.type, DateUtils.toLocalDate(it.membershipStart),
                            it.membershipEnd ? DateUtils.toLocalDate(it.membershipEnd) : null] }
    }
}
