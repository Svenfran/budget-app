package com.github.svenfran.budgetapp.budgetappbackend.db

import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * DIV-122-Migration (DROP TABLE token). Geprüft wird das Schema nach allen Liquibase-Changesets im test-Kontext; seit
 * dem Entfernen von Entity und Repository braucht kein anderer Test die Tabelle, ein grüner Lauf allein belegt den
 * Drop also nicht. Dass beide Master-Changelogs den Drop einbinden, prüft {@link TokenTableDropChangelogSpec}.
 */
class TokenTableDropIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(TokenTableDropIntegrationSpec)

    def "Tabelle token existiert nach allen Liquibase-Changesets nicht mehr"() {
        given:
        LOG.info('Szenario: Schema nach allen Liquibase-Changesets')

        when:
        def anzahl = entityManager.createNativeQuery("SELECT count(*) FROM information_schema.tables " +
                "WHERE table_schema = current_schema() AND table_name = 'token'")
                .singleResult as Long
        LOG.info('information_schema -> Tabellen token: {}', anzahl)

        then:
        anzahl == 0
    }
}
