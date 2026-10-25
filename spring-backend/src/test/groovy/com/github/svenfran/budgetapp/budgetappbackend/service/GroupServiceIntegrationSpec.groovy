package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.AddGroupMemberDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.ChangeGroupOwnerDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.GroupDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.RemoveGroupMemberDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOfGroupException
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartTemplateRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.CategoryRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupMembershipHistoryRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingItemRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.ShoppingListRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

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
    @Autowired UserRepository userRepository

    def "Neuberechnung setzt den Durchschnitt pro Mitglied auf 0, wenn zum Kaufdatum niemand Mitglied ist"() {
        given: "Owner und Ben sind seit REFERENCE_DATE - 1 Jahr Mitglied"
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def imZeitraum = fixtures.cart(wg, amount: 60d, datePurchased: DateUtils.toDate(REFERENCE_DATE))
        def vorZeitraum = fixtures.cart(wg, amount: 40d, datePurchased: DateUtils.toDate(REFERENCE_DATE.minusYears(2)))
        flushAndClear()
        LOG.info('Szenario: Gruppe {}, Owner {}, Ben {} (Mitglieder ab {}); Einkauf {} 60.00 am {}, Einkauf {} 40.00 am {}',
                wg.group.id, wg.owner.id, ben.id, REFERENCE_DATE.minusYears(1), imZeitraum.id, REFERENCE_DATE,
                vorZeitraum.id, REFERENCE_DATE.minusYears(2))

        when:
        groupService.calculateAveragePerMember(groupRepository.findById(wg.group.id).get())
        flushAndClear()
        def averageImZeitraum = cartRepository.findById(imZeitraum.id).get().averagePerMember
        def averageVorZeitraum = cartRepository.findById(vorZeitraum.id).get().averagePerMember
        LOG.info('groupService.calculateAveragePerMember(Gruppe {}) -> averagePerMember Einkauf {}: {}, Einkauf {}: {}',
                wg.group.id, imZeitraum.id, averageImZeitraum, vorZeitraum.id, averageVorZeitraum)

        then:
        averageImZeitraum == 30d
        averageVorZeitraum == 0d
    }

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

    def "Hinzufügen rechnet den Durchschnitt aller Einkäufe der Gruppe ab dem Eintrittstag neu"() {
        given: "Owner und Ben seit 2024-06-15; beide Einkäufe mit falschem Durchschnitt 99, damit die Neuberechnung sichtbar ist"
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
        groupService.addMemberToGroup(new AddGroupMemberDto(id: groupId, newMemberEmail: cleoEmail))
        flushAndClear()
        def averageVorEintritt = cartRepository.findById(vorEintritt.id).get().averagePerMember
        def averageAmEintritt = cartRepository.findById(amEintritt.id).get().averagePerMember
        LOG.info('groupService.addMemberToGroup(Gruppe {}, Cleo) -> averagePerMember Einkauf {}: {}, Einkauf {}: {}',
                groupId, vorEintritt.id, averageVorEintritt, amEintritt.id, averageAmEintritt)

        then: "am Tag vor dem Eintritt 2 Mitglieder, am Eintrittstag (inklusiv) 3"
        averageVorEintritt == 30d
        averageAmEintritt == 20d
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

    def "Entfernen rechnet den Durchschnitt neu: der Austrittstag zählt noch mit"() {
        given: "Owner und Ben seit 2024-06-15; Einkäufe mit falschem Durchschnitt 99, damit die Neuberechnung sichtbar ist"
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
        groupService.removeMemberFromGroup(new RemoveGroupMemberDto(id: groupId, member: new UserDto(id: benId)))
        flushAndClear()
        def averageAmAustritt = cartRepository.findById(amAustritt.id).get().averagePerMember
        def averageNachAustritt = cartRepository.findById(nachAustritt.id).get().averagePerMember
        LOG.info('groupService.removeMemberFromGroup(Gruppe {}, Ben {}) -> averagePerMember Einkauf {}: {}, Einkauf {}: {}',
                groupId, benId, amAustritt.id, averageAmAustritt, nachAustritt.id, averageNachAustritt)

        then: "am Austrittstag (inklusiv) 2 Mitglieder, am Tag danach nur noch der Owner"
        averageAmAustritt == 30d
        averageNachAustritt == 60d
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
        def listen = shoppingListRepository.findAllByGroup_IdOrderById(groupId)
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
