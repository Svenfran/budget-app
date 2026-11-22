package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.CartDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.RemoveGroupMemberDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.SettlementPaymentDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto
import com.github.svenfran.budgetapp.budgetappbackend.entity.Settlement
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.DatePurchasedNotWithinMembershipPeriodException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.InvalidSettlementPaymentException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOrMemberOfGroupException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotPartyOfSettlementException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.ReceiverChangeNotAllowedException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.SettlementNotFoundException
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.CategoryRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupMembershipHistoryRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.SettlementRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder

import java.time.LocalDate

/**
 * Ausgleichszahlungen über SettlementService (DIV-74; die Anlage-Fälle stammen aus CartServiceIntegrationSpec, DIV-14).
 *
 * - Ohne Request liest der Translator die JVM-Locale (lokal de, in CI ggf. en): die Features setzen die Locale und
 *   setzen sie im cleanup:-Block zurück.
 * - Die Settlement-Kategorie wird über das Kennzeichen is_settlement gefunden, nicht über den Namen.
 * - Atomarität nach einem Write und Nebenläufigkeit: SettlementTransactionIntegrationSpec (commit-abhängig).
 */
class SettlementServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(SettlementServiceIntegrationSpec)

    @Autowired SettlementService settlementService
    @Autowired SettlementRepository settlementRepository
    @Autowired CartRepository cartRepository
    @Autowired CategoryRepository categoryRepository
    @Autowired GroupMembershipHistoryRepository membershipRepository
    @Autowired GroupRepository groupRepository
    @Autowired UserRepository userRepository
    @Autowired GroupService groupService

    def cleanup() {
        LocaleContextHolder.resetLocaleContext()
    }

    // ----- Anlegen -----

    def "#rolle zahlt aus: Settlement und zwei verknüpfte, gegenläufige Einkäufe; die Settlement-Kategorie wird mit Kennzeichen angelegt"() {
        given: "Anna ist Owner, Ben Mitglied; es zahlt jeweils die Rolle an den anderen"
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def anna = fixtures.user(name: "Anna${TestData.nextId()}")
        def ben = fixtures.user(name: "Ben${TestData.nextId()}")
        def wg = fixtures.group(owner: anna, members: [ben])
        def (zahler, partner) = rolle == 'Gruppen-Owner' ? [anna, ben] : [ben, anna]
        def (groupId, zahlerId, partnerId, standardKategorieId) = [wg.group.id, zahler.id, partner.id, wg.category.id]
        def (zahlerName, partnerName) = [zahler.name, partner.name]
        def dto = new SettlementPaymentDto(30d, groupId, new UserDto(id: partnerId), TestData.REFERENCE_DATE)
        flushAndClear()
        loginAs(zahler)
        LOG.info('Szenario: Locale de, Gruppe {} ohne Settlement-Kategorie (Standardkategorie {}), Owner {}, Mitglied {}; ' +
                'User {} {} ({}, angemeldet) zahlt 30.00 an User {} {} am {}', groupId, standardKategorieId, anna.id, ben.id,
                zahlerId, zahlerName, rolle, partnerId, partnerName, TestData.REFERENCE_DATE)

        when:
        def result = settlementService.addSettlementPayment(dto)
        flushAndClear()
        def sender = cartRepository.findById(result[0].id).get()
        def empfaenger = cartRepository.findById(result[1].id).get()
        def settlements = settlementsDerGruppe(groupId)
        def kategorien = settlementCategories(groupId)
        LOG.info('settlementService.addSettlementPayment(30.00 an User {}) -> Settlements {}, Cart {} "{}" {} (User {}, avg {}), ' +
                'Cart {} "{}" {} (User {}, avg {}); Settlement-Kategorien {}', partnerId, settlements*.id, sender.id, sender.title,
                sender.amount, sender.user.id, sender.averagePerMember, empfaenger.id, empfaenger.title, empfaenger.amount,
                empfaenger.user.id, empfaenger.averagePerMember, kategorien*.id)

        then: "Carts werden nicht live synchronisiert"
        0 * notificationService._

        and: "genau ein Settlement mit Zahler, Empfänger, Betrag, Datum und Ersteller"
        settlements.size() == 1
        def settlement = settlements.first()
        settlement.payer.id == zahlerId
        settlement.receiver.id == partnerId
        settlement.createdBy.id == zahlerId
        settlement.amount == 30d
        DateUtils.toLocalDate(settlement.datePurchased) == TestData.REFERENCE_DATE

        and: "beide Carts im Ergebnis tragen die Settlement-Id"
        result*.settlementId == [settlement.id, settlement.id]

        and: "genau eine neue, gekennzeichnete Settlement-Kategorie neben der Standardkategorie"
        kategorien.size() == 1
        def kategorieId = kategorien.first().id
        kategorieId != standardKategorieId
        kategorien.first().name == 'Ausgleichszahlung'

        and: "Sender bucht +30 an den Empfänger"
        sender.settlement.id == settlement.id
        sender.title == "Ausgleichszahlung an ${partnerName}".toString()
        sender.user.id == zahlerId
        sender.amount == 30d
        sender.averagePerMember == 15d
        sender.category.id == kategorieId
        DateUtils.toLocalDate(sender.datePurchased) == TestData.REFERENCE_DATE

        and: "Empfänger bucht -30 vom Sender"
        empfaenger.settlement.id == settlement.id
        empfaenger.title == "Ausgleichszahlung von ${zahlerName}".toString()
        empfaenger.user.id == partnerId
        empfaenger.amount == -30d
        empfaenger.averagePerMember == -15d
        empfaenger.category.id == kategorieId
        DateUtils.toLocalDate(empfaenger.datePurchased) == TestData.REFERENCE_DATE

        where:
        rolle << ['Gruppen-Owner', 'Mitglied']
    }

    def "Die gekennzeichnete Kategorie wird auch nach Umbenennung verwendet, eine ungekennzeichnete namens 'Ausgleichszahlung' nicht"() {
        given:
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def namensgleich = fixtures.category(wg, name: 'Ausgleichszahlung')
        def umbenannt = fixtures.category(wg, name: 'Rückzahlungen', settlement: true)
        def (groupId, namensgleichId, umbenanntId) = [wg.group.id, namensgleich.id, umbenannt.id]
        def dto = new SettlementPaymentDto(30d, groupId, new UserDto(id: ben.id), TestData.REFERENCE_DATE)
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Locale de, Gruppe {} mit ungekennzeichneter Kategorie {} "Ausgleichszahlung" und gekennzeichneter ' +
                'Kategorie {} "Rückzahlungen"; Owner {} zahlt 30.00 an Ben {}', groupId, namensgleichId, umbenanntId, wg.owner.id, ben.id)

        when:
        def result = settlementService.addSettlementPayment(dto)
        flushAndClear()
        def kategorien = result.collect { cartRepository.findById(it.id).get().category.id }
        def settlementKategorien = settlementCategories(groupId)*.id
        LOG.info('settlementService.addSettlementPayment(30.00 an User {}) -> Carts {} mit Kategorien {}; gekennzeichnet {}',
                ben.id, result*.id, kategorien, settlementKategorien)

        then:
        kategorien == [umbenanntId, umbenanntId]
        settlementKategorien == [umbenanntId]
    }

    def "Anlage mit Locale #locale nutzt die vorhandene gekennzeichnete Kategorie, keine zweite in Request-Sprache"() {
        given:
        LocaleContextHolder.setLocale(locale)
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def vorhanden = fixtures.category(wg, name: 'Ausgleichszahlung', settlement: true)
        def (groupId, vorhandenId) = [wg.group.id, vorhanden.id]
        def dto = new SettlementPaymentDto(30d, groupId, new UserDto(id: ben.id), TestData.REFERENCE_DATE)
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Locale {}, Gruppe {} mit gekennzeichneter Kategorie {} "Ausgleichszahlung", Owner {} zahlt an Ben {}',
                locale, groupId, vorhandenId, wg.owner.id, ben.id)

        when:
        def result = settlementService.addSettlementPayment(dto)
        flushAndClear()
        def kategorien = result.collect { cartRepository.findById(it.id).get().category.id }
        def alleKategorieNamen = categoryRepository.findAllByGroup_IdOrderByName(groupId)*.name
        LOG.info('settlementService.addSettlementPayment -> Kategorien {}; Kategorien der Gruppe {}', kategorien, alleKategorieNamen)

        then:
        kategorien == [vorhandenId, vorhandenId]
        settlementCategories(groupId)*.id == [vorhandenId]
        !alleKategorieNamen.contains(neuerName)

        where:
        locale         | neuerName
        Locale.ENGLISH | 'Repayment'
        new Locale('es') | 'Repago'
    }

    def "Lange Namen werden im Titel der Ausgleichszahlung nach 10 Zeichen gekürzt"() {
        given:
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user(name: "Maximilian${TestData.nextId()}")
        def wg = fixtures.group(members: [ben])
        def (groupId, benName) = [wg.group.id, ben.name]
        def dto = new SettlementPaymentDto(30d, groupId, new UserDto(id: ben.id), TestData.REFERENCE_DATE)
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Locale de, Gruppe {}, Owner {} (angemeldet), Mitglied {} "{}" ({} Zeichen); Ausgleichszahlung an ihn',
                groupId, wg.owner.id, ben.id, benName, benName.length())

        when:
        def result = settlementService.addSettlementPayment(dto)
        flushAndClear()
        def senderTitle = cartRepository.findById(result[0].id).get().title
        LOG.info('settlementService.addSettlementPayment(an User {}) -> Titel "{}"', ben.id, senderTitle)

        then:
        senderTitle == 'Ausgleichszahlung an Maximilian...'
    }

    def "Erhaltene Zahlung (Phase 5): Ben erfasst „erhalten von Owner“ – Zahler ist der Owner, Ersteller Ben"() {
        given:
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user(name: "Ben${TestData.nextId()}")
        def wg = fixtures.group(members: [ben])
        def (groupId, ownerId, benId, ownerName, benName) = [wg.group.id, wg.owner.id, ben.id, wg.owner.name, ben.name]
        def dto = new SettlementPaymentDto(25d, groupId, new UserDto(id: benId), TestData.REFERENCE_DATE)
        dto.payer = new UserDto(id: ownerId)
        flushAndClear()
        loginAs(userRepository.findById(benId).get())
        LOG.info('Szenario: Gruppe {}, Owner {}, Ben {} (angemeldet) erfasst 25.00 erhalten vom Owner', groupId, ownerId, benId)

        when:
        def result = settlementService.addSettlementPayment(dto)
        flushAndClear()
        def settlement = settlementsDerGruppe(groupId).first()
        def carts = result*.id.collect { cartRepository.findById(it).get() }
        LOG.info('settlementService.addSettlementPayment(erhalten von {}) -> Settlement {} Zahler {}, Empfänger {}, Ersteller {}; ' +
                'Carts User {}, Beträge {}', ownerId, settlement.id, settlement.payer.id, settlement.receiver.id,
                settlement.createdBy.id, carts*.user*.id, carts*.amount)

        then:
        settlement.payer.id == ownerId
        settlement.receiver.id == benId
        settlement.createdBy.id == benId
        settlement.amount == 25d
        carts*.user*.id == [ownerId, benId]
        carts*.amount == [25d, -25d]
        carts*.title == ["Ausgleichszahlung an ${benName}".toString(), "Ausgleichszahlung von ${ownerName}".toString()]
    }

    def "Anlegen durch einen Unbeteiligten (Phase 5): Cleo erfasst eine Zahlung Owner → Ben, nichts angelegt"() {
        given:
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user()
        def cleo = fixtures.user()
        def wg = fixtures.group(members: [ben, cleo])
        def groupId = wg.group.id
        def dto = new SettlementPaymentDto(25d, groupId, new UserDto(id: ben.id), TestData.REFERENCE_DATE)
        dto.payer = new UserDto(id: wg.owner.id)
        flushAndClear()
        loginAs(userRepository.findById(cleo.id).get())
        LOG.info('Szenario: Gruppe {}, Owner {}, Ben {}, Cleo {} (angemeldet) erfasst Owner -> Ben', groupId, wg.owner.id, ben.id, cleo.id)

        when:
        LOG.info('settlementService.addSettlementPayment(Owner -> Ben) als Cleo, erwartet NotPartyOfSettlementException')
        settlementService.addSettlementPayment(dto)

        then:
        thrown(NotPartyOfSettlementException)
        nichtsAngelegt(groupId)
    }

    // ----- Ablehnungen: keine Zeile wird geschrieben -----

    def "#fall wird abgelehnt, weder Settlement noch Settlement-Kategorie noch Einkäufe werden angelegt"() {
        given: "Fremder ist weder Owner noch Mitglied der Gruppe"
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def fremder = fixtures.user()
        def (zahler, empfaenger) = fall == 'Ausgleichszahlung an ein Nicht-Mitglied' ? [wg.owner, fremder] : [fremder, ben]
        def groupId = wg.group.id
        def dto = new SettlementPaymentDto(30d, groupId, new UserDto(id: empfaenger.id),
                TestData.REFERENCE_DATE)
        flushAndClear()
        loginAs(zahler)
        LOG.info('Szenario: Gruppe {}, Owner {}, Ben {}, Fremder {}; angemeldet User {} zahlt an User {}', groupId,
                wg.owner.id, ben.id, fremder.id, zahler.id, empfaenger.id)

        when:
        LOG.info('settlementService.addSettlementPayment(an User {}) als User {}, erwartet NotOwnerOrMemberOfGroupException',
                empfaenger.id, zahler.id)
        settlementService.addSettlementPayment(dto)

        then:
        thrown(NotOwnerOrMemberOfGroupException)
        nichtsAngelegt(groupId)

        where:
        fall << ['Ausgleichszahlung an ein Nicht-Mitglied', 'Ausgleichszahlung durch ein Nicht-Mitglied']
    }

    def "Kaufdatum außerhalb der Mitgliedschaft des #wer wird abgelehnt, nichts angelegt"() {
        given: "Owner zahlt am REFERENCE_DATE an Ben; einer der beiden ist erst ab dem Folgetag Mitglied"
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def spaeter = wer == 'Senders' ? wg.owner : ben
        def membership = membershipRepository.findByGroupIdAndUserId(groupId, spaeter.id).first()
        membership.membershipStart = DateUtils.toDate(TestData.REFERENCE_DATE.plusDays(1))
        membershipRepository.save(membership)
        def dto = new SettlementPaymentDto(30d, groupId, new UserDto(id: ben.id), TestData.REFERENCE_DATE)
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet, Sender), Ben {} (Empfänger); User {} Mitglied erst ab {}; ' +
                'Kaufdatum {}', groupId, wg.owner.id, ben.id, spaeter.id, TestData.REFERENCE_DATE.plusDays(1),
                TestData.REFERENCE_DATE)

        when:
        LOG.info('settlementService.addSettlementPayment(an User {} am {}), erwartet DatePurchasedNotWithinMembershipPeriodException',
                ben.id, TestData.REFERENCE_DATE)
        settlementService.addSettlementPayment(dto)

        then:
        thrown(DatePurchasedNotWithinMembershipPeriodException)
        nichtsAngelegt(groupId)

        where:
        wer << ['Senders', 'Empfängers']
    }

    def "Ungültige Anfrage (#fall) wird mit InvalidSettlementPaymentException abgelehnt, nichts angelegt"() {
        given:
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def groupId = wg.group.id
        def empfaengerId = an == 'Ben' ? ben.id : wg.owner.id
        def dto = new SettlementPaymentDto(betrag, ohneGruppe ? null : groupId,
                ohneEmpfaenger ? null : new UserDto(id: ohneEmpfaengerId ? null : empfaengerId),
                ohneDatum ? null : TestData.REFERENCE_DATE)
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet), Ben {}; Anfrage: Betrag {}, Gruppe {}, Empfänger {}, Datum {}',
                groupId, wg.owner.id, ben.id, betrag, dto.groupId, dto.member?.id, dto.datePurchased)

        when:
        LOG.info('settlementService.addSettlementPayment({}), erwartet InvalidSettlementPaymentException', fall)
        settlementService.addSettlementPayment(dto)

        then:
        def e = thrown(InvalidSettlementPaymentException)
        e.message.contains(meldung)
        nichtsAngelegt(groupId)

        where:
        fall                    | betrag | an      | ohneGruppe | ohneEmpfaenger | ohneEmpfaengerId | ohneDatum || meldung
        'Betrag 0'              | 0d     | 'Ben'   | false      | false          | false            | false     || 'greater than 0'
        'Betrag negativ'        | -5d    | 'Ben'   | false      | false          | false            | false     || 'greater than 0'
        'Betrag 3 Nachkomma'    | 1.234d | 'Ben'   | false      | false          | false            | false     || 'at most 2 decimal'
        'Betrag fehlt'          | null   | 'Ben'   | false      | false          | false            | false     || "'amount'"
        'Gruppe fehlt'          | 30d    | 'Ben'   | true       | false          | false            | false     || "'groupId'"
        'Empfänger fehlt'       | 30d    | 'Ben'   | false      | true           | false            | false     || "'member'"
        'Empfänger-Id fehlt'    | 30d    | 'Ben'   | false      | false          | true             | false     || "'member.id'"
        'Datum fehlt'           | 30d    | 'Ben'   | false      | false          | false            | true      || "'datePurchased'"
        'Zahler gleich Empfänger' | 30d  | 'selbst' | false     | false          | false            | false     || 'must differ'
    }

    // ----- Löschen (Phase 2) -----

    def "#rolle löscht die Ausgleichszahlung: Settlement und beide Einkäufe sind weg, andere Einkäufe bleiben"() {
        given:
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def normal = fixtures.cart(wg, title: 'Pizza', amount: 20d)
        flushAndClear()
        loginAs(wg.owner)
        def paar = settlementService.addSettlementPayment(
                new SettlementPaymentDto(30d, wg.group.id, new UserDto(id: ben.id), TestData.REFERENCE_DATE))
        def settlementId = paar[0].settlementId
        flushAndClear()
        def loeschender = rolle == 'Zahler' ? wg.owner : ben
        loginAs(loeschender)
        LOG.info('Szenario: Gruppe {}, Owner {} zahlt an Ben {}; Settlement {} mit Carts {}, normaler Einkauf {}; User {} ({}) löscht',
                wg.group.id, wg.owner.id, ben.id, settlementId, paar*.id, normal.id, loeschender.id, rolle)

        when:
        settlementService.deleteSettlementPayment(settlementId)
        flushAndClear()
        def settlementVorhanden = settlementRepository.existsById(settlementId)
        def verbliebeneCarts = cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(wg.group.id)*.id
        LOG.info('settlementService.deleteSettlementPayment({}) -> Settlement vorhanden {}, verbliebene Carts {}', settlementId,
                settlementVorhanden, verbliebeneCarts)

        then:
        0 * notificationService._
        !settlementVorhanden
        verbliebeneCarts == [normal.id]

        where:
        rolle << ['Zahler', 'Empfänger']
    }

    def "#fall darf die Ausgleichszahlung nicht löschen, Settlement und beide Einkäufe bleiben"() {
        given: "Owner zahlt an Ben; Cleo ist weiteres Mitglied, Fremder kein Mitglied"
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user()
        def cleo = fixtures.user()
        def fremder = fixtures.user()
        def wg = fixtures.group(members: [ben, cleo])
        flushAndClear()
        loginAs(wg.owner)
        def paar = settlementService.addSettlementPayment(
                new SettlementPaymentDto(30d, wg.group.id, new UserDto(id: ben.id), TestData.REFERENCE_DATE))
        def settlementId = paar[0].settlementId
        if (fall == 'Entfernter Empfänger') {
            entferne(wg.group.id, ben.id)
        }
        flushAndClear()
        def handelnder = [Dritter: cleo, 'Nicht-Mitglied': fremder, 'Entfernter Empfänger': ben][fall]
        loginAs(userRepository.findById(handelnder.id).get())
        LOG.info('Szenario: Gruppe {}, Owner {} zahlt an Ben {}, Cleo {}, Fremder {}; Settlement {}; User {} ({}) löscht, erwartet {}',
                wg.group.id, wg.owner.id, ben.id, cleo.id, fremder.id, settlementId, handelnder.id, fall, erwartet.simpleName)

        when:
        settlementService.deleteSettlementPayment(settlementId)

        then:
        thrown(erwartet)
        settlementRepository.existsById(settlementId)
        paar.every { cartRepository.existsById(it.id) }

        where:
        fall                   || erwartet
        'Dritter'              || NotPartyOfSettlementException
        'Nicht-Mitglied'       || NotOwnerOrMemberOfGroupException
        'Entfernter Empfänger' || NotOwnerOrMemberOfGroupException
    }

    def "Hat der Empfänger die Gruppe verlassen, darf der Zahler die Ausgleichszahlung löschen (A8)"() {
        given:
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        flushAndClear()
        loginAs(wg.owner)
        def paar = settlementService.addSettlementPayment(
                new SettlementPaymentDto(30d, wg.group.id, new UserDto(id: ben.id), TestData.REFERENCE_DATE))
        def settlementId = paar[0].settlementId
        entferne(wg.group.id, ben.id)
        flushAndClear()
        loginAs(userRepository.findById(wg.owner.id).get())
        LOG.info('Szenario: Gruppe {}, Owner {} zahlte an Ben {}, Ben ist nicht mehr Mitglied; Settlement {}; Owner löscht',
                wg.group.id, wg.owner.id, ben.id, settlementId)

        when:
        settlementService.deleteSettlementPayment(settlementId)
        flushAndClear()
        def settlementVorhanden = settlementRepository.existsById(settlementId)
        def verbliebeneCarts = paar*.id.findAll { cartRepository.existsById(it) }
        LOG.info('settlementService.deleteSettlementPayment({}) -> Settlement vorhanden {}, verbliebene Carts {}', settlementId,
                settlementVorhanden, verbliebeneCarts)

        then:
        !settlementVorhanden
        verbliebeneCarts.isEmpty()
    }

    def "Löschen einer unbekannten Ausgleichszahlung wirft SettlementNotFoundException"() {
        given:
        def wg = fixtures.group()
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet); Settlement -1 existiert nicht', wg.group.id, wg.owner.id)

        when:
        LOG.info('settlementService.deleteSettlementPayment(-1), erwartet SettlementNotFoundException')
        settlementService.deleteSettlementPayment(-1L)

        then:
        thrown(SettlementNotFoundException)
    }

    // ----- Bearbeiten (Phase 3) -----

    def "#rolle ändert Betrag und Datum: Settlement und beide Einkäufe synchron, Ids bleiben"() {
        given: "Owner zahlte am REFERENCE_DATE 30.00 an Ben; neu: 45.50 zehn Tage früher"
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user(name: "Ben${TestData.nextId()}")
        def wg = fixtures.group(members: [ben])
        def paar = anlegen(wg.owner, ben, wg.group.id, 30d)
        def (settlementId, ownerName, benName) = [paar[0].settlementId, wg.owner.name, ben.name]
        def neuesDatum = TestData.REFERENCE_DATE.minusDays(10)
        loginAs(rolle == 'Zahler' ? wg.owner : userRepository.findById(ben.id).get())
        LOG.info('Szenario: Gruppe {}, Owner {} zahlte 30.00 an Ben {} (Settlement {}, Carts {}); {} ändert auf 45.50 am {}',
                wg.group.id, wg.owner.id, ben.id, settlementId, paar*.id, rolle, neuesDatum)

        when:
        def result = settlementService.updateSettlementPayment(dto(settlementId, 45.5d, ben.id, neuesDatum))
        flushAndClear()
        def settlement = settlementRepository.findById(settlementId).get()
        def carts = paar*.id.collect { cartRepository.findById(it).get() }
        LOG.info('settlementService.updateSettlementPayment({}) -> Settlement {} {} am {}; Carts {} Beträge {}, avg {}, Daten {}',
                settlementId, settlement.id, settlement.amount, settlement.datePurchased, carts*.id, carts*.amount,
                carts*.averagePerMember, carts*.datePurchased)

        then:
        0 * notificationService._
        result*.id == paar*.id
        result*.amount == [45.5d, -45.5d]
        settlement.amount == 45.5d
        DateUtils.toLocalDate(settlement.datePurchased) == neuesDatum
        settlement.receiver.id == ben.id
        carts*.amount == [45.5d, -45.5d]
        carts*.averagePerMember == [22.75d, -22.75d]
        carts.every { DateUtils.toLocalDate(it.datePurchased) == neuesDatum }
        carts*.title == ["Ausgleichszahlung an ${benName}".toString(), "Ausgleichszahlung von ${ownerName}".toString()]

        where:
        rolle << ['Zahler', 'Empfänger']
    }

    def "Zahler wechselt den Empfänger: Empfänger-Einkauf gehört dem neuen Empfänger, beide Titel neu"() {
        given:
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user(name: "Ben${TestData.nextId()}")
        def cleo = fixtures.user(name: "Cleo${TestData.nextId()}")
        def wg = fixtures.group(members: [ben, cleo])
        def paar = anlegen(wg.owner, ben, wg.group.id, 30d)
        def (settlementId, ownerName, cleoName) = [paar[0].settlementId, wg.owner.name, cleo.name]
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} zahlte 30.00 an Ben {} (Settlement {}, Carts {}); Owner wechselt zu Cleo {}',
                wg.group.id, wg.owner.id, ben.id, settlementId, paar*.id, cleo.id)

        when:
        settlementService.updateSettlementPayment(dto(settlementId, 30d, cleo.id, TestData.REFERENCE_DATE))
        flushAndClear()
        def settlement = settlementRepository.findById(settlementId).get()
        def carts = paar*.id.collect { cartRepository.findById(it).get() }
        LOG.info('settlementService.updateSettlementPayment(Empfänger -> {}) -> Settlement-Empfänger {}, Carts {} User {}, Titel {}',
                cleo.id, settlement.receiver.id, carts*.id, carts*.user*.id, carts*.title)

        then:
        settlement.payer.id == wg.owner.id
        settlement.receiver.id == cleo.id
        carts*.user*.id == [wg.owner.id, cleo.id]
        carts*.amount == [30d, -30d]
        carts*.title == ["Ausgleichszahlung an ${cleoName}".toString(), "Ausgleichszahlung von ${ownerName}".toString()]
        carts.every { !it.deleted }
    }

    def "Bearbeiten wird abgelehnt (#fall), Settlement und beide Einkäufe bleiben unverändert"() {
        given: "Owner zahlte 30.00 an Ben; Cleo ist weiteres Mitglied, Fremder kein Mitglied"
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user()
        def cleo = fixtures.user()
        def fremder = fixtures.user()
        def wg = fixtures.group(members: [ben, cleo])
        def paar = anlegen(wg.owner, ben, wg.group.id, 30d)
        def settlementId = paar[0].settlementId
        def nutzer = [owner: wg.owner, ben: ben, cleo: cleo, fremder: fremder]
        loginAs(userRepository.findById(nutzer[handelnder].id).get())
        def anfrage = dto(id ?: settlementId, betrag, nutzer[empfaenger].id, TestData.REFERENCE_DATE.plusDays(tageVerschoben))
        LOG.info('Szenario: Gruppe {}, Owner {} zahlte 30.00 an Ben {} (Settlement {}); Cleo {}, Fremder {}; {} ändert: Betrag {}, ' +
                'Empfänger {}, Datum {}, erwartet {}', wg.group.id, wg.owner.id, ben.id, settlementId, cleo.id, fremder.id,
                handelnder, betrag, empfaenger, anfrage.datePurchased, erwartet.simpleName)

        when:
        settlementService.updateSettlementPayment(anfrage)

        then:
        thrown(erwartet)
        unveraendert(settlementId, paar*.id, ben.id)

        where:
        fall                                  | handelnder | empfaenger | betrag | tageVerschoben | id   || erwartet
        'Empfänger wechselt den Empfänger'    | 'ben'      | 'cleo'     | 30d    | 0              | null || ReceiverChangeNotAllowedException
        'Dritter Mitglied'                    | 'cleo'     | 'ben'      | 40d    | 0              | null || NotPartyOfSettlementException
        'Nicht-Mitglied'                      | 'fremder'  | 'ben'      | 40d    | 0              | null || NotOwnerOrMemberOfGroupException
        'neuer Empfänger ist der Zahler'      | 'owner'    | 'owner'    | 30d    | 0              | null || InvalidSettlementPaymentException
        'neuer Empfänger ist kein Mitglied'   | 'owner'    | 'fremder'  | 30d    | 0              | null || NotOwnerOrMemberOfGroupException
        'Datum vor der Mitgliedschaft'        | 'owner'    | 'ben'      | 30d    | -800           | null || DatePurchasedNotWithinMembershipPeriodException
        'Betrag 0'                            | 'owner'    | 'ben'      | 0d     | 0              | null || InvalidSettlementPaymentException
        'unbekanntes Settlement'              | 'owner'    | 'ben'      | 30d    | 0              | -1L  || SettlementNotFoundException
    }

    def "Hat der Empfänger die Gruppe verlassen: Zahler ändert den Betrag (Löschkennzeichen bleibt), ein Datum nach dem Austritt wird abgelehnt (A8)"() {
        given: "Ben wird am REFERENCE_DATE (testClock) entfernt"
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def paar = anlegen(wg.owner, ben, wg.group.id, 30d)
        def settlementId = paar[0].settlementId
        entferne(wg.group.id, ben.id)
        flushAndClear()
        loginAs(userRepository.findById(wg.owner.id).get())
        LOG.info('Szenario: Gruppe {}, Owner {} zahlte 30.00 an Ben {} (Settlement {}, Carts {}); Ben ist seit {} kein Mitglied',
                wg.group.id, wg.owner.id, ben.id, settlementId, paar*.id, TestData.REFERENCE_DATE)

        when: "Betrag ändern, Datum bleibt"
        settlementService.updateSettlementPayment(dto(settlementId, 50d, ben.id, TestData.REFERENCE_DATE))
        flushAndClear()
        def carts = paar*.id.collect { cartRepository.findById(it).get() }
        LOG.info('settlementService.updateSettlementPayment(50.00) -> Beträge {}, gelöscht {}', carts*.amount, carts*.deleted)

        then:
        carts*.amount == [50d, -50d]
        carts*.deleted == [false, true]

        when: "Datum nach dem Austritt"
        LOG.info('settlementService.updateSettlementPayment(Datum {}), erwartet DatePurchasedNotWithinMembershipPeriodException',
                TestData.REFERENCE_DATE.plusDays(5))
        settlementService.updateSettlementPayment(dto(settlementId, 50d, ben.id, TestData.REFERENCE_DATE.plusDays(5)))

        then:
        thrown(DatePurchasedNotWithinMembershipPeriodException)
    }

    def "Fehlt ein Einkauf des Paars, wird das Bearbeiten abgelehnt und das Settlement nicht geändert"() {
        given: "Altdaten-Fall: nur noch ein verknüpfter Einkauf"
        LocaleContextHolder.setLocale(Locale.GERMAN)
        def ben = fixtures.user()
        def wg = fixtures.group(members: [ben])
        def paar = anlegen(wg.owner, ben, wg.group.id, 30d)
        def settlementId = paar[0].settlementId
        cartRepository.deleteById(paar[1].id)
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Settlement {} hat nur noch Cart {}', wg.group.id, settlementId, paar[0].id)

        when:
        LOG.info('settlementService.updateSettlementPayment(40.00), erwartet IllegalStateException')
        settlementService.updateSettlementPayment(dto(settlementId, 40d, ben.id, TestData.REFERENCE_DATE))

        then:
        thrown(IllegalStateException)
        settlementRepository.findById(settlementId).get().amount == 30d
        cartRepository.findById(paar[0].id).get().amount == 30d
    }

    /** Ausgleichszahlung wie die App anlegen (als Zahler), danach Persistence Context leeren. */
    private List<CartDto> anlegen(def zahler, def empfaenger, Long groupId, Double betrag) {
        loginAs(zahler)
        def paar = settlementService.addSettlementPayment(
                new SettlementPaymentDto(betrag, groupId, new UserDto(id: empfaenger.id), TestData.REFERENCE_DATE))
        flushAndClear()
        paar
    }

    private static SettlementPaymentDto dto(Long settlementId, Double betrag, Long empfaengerId, LocalDate datum) {
        def dto = new SettlementPaymentDto(betrag, null, new UserDto(id: empfaengerId), datum)
        dto.id = settlementId
        dto
    }

    /** Settlement und Paar im Ausgangszustand von anlegen(owner, ben, 30.00). */
    private boolean unveraendert(Long settlementId, List<Long> cartIds, Long benId) {
        flushAndClear()
        def settlement = settlementRepository.findById(settlementId).get()
        def carts = cartIds.collect { cartRepository.findById(it).get() }
        assert settlement.amount == 30d
        assert settlement.receiver.id == benId
        assert DateUtils.toLocalDate(settlement.datePurchased) == TestData.REFERENCE_DATE
        assert carts*.amount == [30d, -30d]
        assert carts[1].user.id == benId
        true
    }

    /** Entfernt ein Mitglied über GroupService wie die App (beendet die Mitgliedschaft, markiert seine Einkäufe). */
    private void entferne(Long groupId, Long memberId) {
        loginAs(groupRepository.findById(groupId).get().owner)
        groupService.removeMemberFromGroup(new RemoveGroupMemberDto(id: groupId, member: new UserDto(id: memberId)))
        LOG.info('groupService.removeMemberFromGroup(Gruppe {}, User {}) aufgerufen', groupId, memberId)
    }

    private List<Settlement> settlementsDerGruppe(Long groupId) {
        entityManager.createQuery('select s from Settlement s where s.group.id = :groupId order by s.id', Settlement)
                .setParameter('groupId', groupId).resultList
    }

    private List settlementCategories(Long groupId) {
        categoryRepository.findAllByGroup_IdOrderByName(groupId).findAll { it.settlement }
    }

    /** Weder Settlement noch gekennzeichnete Kategorie noch Einkauf in der Gruppe. */
    private boolean nichtsAngelegt(Long groupId) {
        assert settlementsDerGruppe(groupId).isEmpty()
        assert settlementCategories(groupId).isEmpty()
        assert cartRepository.findCartsByGroupIdOrderByDatePurchasedDesc(groupId).isEmpty()
        true
    }
}
