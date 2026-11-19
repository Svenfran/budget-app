package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto
import com.github.svenfran.budgetapp.budgetappbackend.repository.CategoryRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

/**
 * Settlement-Kennzeichen der Kategorie (DIV-74) über die Kategorie-Schreibwege: Es kommt nie vom Client und darf weder
 * beim Umbenennen noch über eine beim Anlegen mitgesendete Id verloren gehen.
 */
class CategoryServiceIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(CategoryServiceIntegrationSpec)

    @Autowired CategoryService categoryService
    @Autowired CategoryRepository categoryRepository

    def "Umbenennen einer Settlement-Kategorie behält das Kennzeichen"() {
        given:
        def wg = fixtures.group()
        def kategorie = fixtures.category(wg, name: 'Ausgleichszahlung', settlement: true)
        def (groupId, kategorieId) = [wg.group.id, kategorie.id]
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet), Settlement-Kategorie {} "Ausgleichszahlung"', groupId,
                wg.owner.id, kategorieId)

        when:
        categoryService.updateCategory(new CategoryDto(id: kategorieId, name: 'Rückzahlungen', groupId: groupId))
        flushAndClear()
        def gespeichert = categoryRepository.findById(kategorieId).get()
        LOG.info('categoryService.updateCategory(Kategorie {} -> "Rückzahlungen") -> Name "{}", Kennzeichen {}', kategorieId,
                gespeichert.name, gespeichert.settlement)

        then:
        gespeichert.name == 'Rückzahlungen'
        gespeichert.settlement
    }

    def "Anlegen mit der Id einer bestehenden Settlement-Kategorie legt eine neue Kategorie an und lässt die bestehende unverändert"() {
        given: "Regression Review 2, F-001: save() hätte die bestehende Kategorie per merge überschrieben"
        def wg = fixtures.group()
        def kategorie = fixtures.category(wg, name: 'Ausgleichszahlung', settlement: true)
        def (groupId, kategorieId) = [wg.group.id, kategorie.id]
        flushAndClear()
        loginAs(wg.owner)
        LOG.info('Szenario: Gruppe {}, Owner {} (angemeldet), Settlement-Kategorie {}; Anlage "Getränke" mit mitgesendeter Id {}',
                groupId, wg.owner.id, kategorieId, kategorieId)

        when:
        def neu = categoryService.addCategory(new CategoryDto(id: kategorieId, name: 'Getränke', groupId: groupId))
        flushAndClear()
        def bestehend = categoryRepository.findById(kategorieId).get()
        def neuGespeichert = categoryRepository.findById(neu.id).get()
        LOG.info('categoryService.addCategory(Id {}, "Getränke") -> neue Kategorie {} (Kennzeichen {}); bestehende {} "{}" (Kennzeichen {})',
                kategorieId, neu.id, neuGespeichert.settlement, kategorieId, bestehend.name, bestehend.settlement)

        then:
        neu.id != kategorieId
        neuGespeichert.name == 'Getränke'
        !neuGespeichert.settlement

        and:
        bestehend.name == 'Ausgleichszahlung'
        bestehend.settlement
    }
}
