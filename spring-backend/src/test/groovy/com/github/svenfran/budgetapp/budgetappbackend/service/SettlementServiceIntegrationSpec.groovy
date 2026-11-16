package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.SettlementPaymentDto
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto
import com.github.svenfran.budgetapp.budgetappbackend.entity.Settlement
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.DatePurchasedNotWithinMembershipPeriodException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.InvalidSettlementPaymentException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOrMemberOfGroupException
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.CartRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.CategoryRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.GroupMembershipHistoryRepository
import com.github.svenfran.budgetapp.budgetappbackend.repository.SettlementRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.i18n.LocaleContextHolder

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
        def dto = new SettlementPaymentDto(30d, groupId, new UserDto(id: partnerId), DateUtils.toDate(TestData.REFERENCE_DATE))
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
        def dto = new SettlementPaymentDto(30d, groupId, new UserDto(id: ben.id), DateUtils.toDate(TestData.REFERENCE_DATE))
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
        def dto = new SettlementPaymentDto(30d, groupId, new UserDto(id: ben.id), DateUtils.toDate(TestData.REFERENCE_DATE))
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
        def dto = new SettlementPaymentDto(30d, groupId, new UserDto(id: ben.id), DateUtils.toDate(TestData.REFERENCE_DATE))
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
                DateUtils.toDate(TestData.REFERENCE_DATE))
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
        def dto = new SettlementPaymentDto(30d, groupId, new UserDto(id: ben.id), DateUtils.toDate(TestData.REFERENCE_DATE))
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
                ohneDatum ? null : DateUtils.toDate(TestData.REFERENCE_DATE))
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
