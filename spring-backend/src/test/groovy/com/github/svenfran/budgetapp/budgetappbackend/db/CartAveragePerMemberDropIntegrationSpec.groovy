package com.github.svenfran.budgetapp.budgetappbackend.db

import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * DIV-40-Migration (DROP COLUMN cart.average_per_member). Geprüft wird das Schema nach allen Liquibase-Changesets im
 * test-Kontext. Dass die übrigen Cart-Daten einer bestehenden Datenbank unverändert bleiben, wird vor dem Merge auf
 * einer Kopie der Dev-DB nachgewiesen (siehe PR).
 */
class CartAveragePerMemberDropIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(CartAveragePerMemberDropIntegrationSpec)

    def "Spalte cart.average_per_member existiert nach allen Liquibase-Changesets nicht mehr"() {
        given:
        LOG.info('Szenario: Schema nach allen Liquibase-Changesets')

        when:
        def anzahl = entityManager.createNativeQuery("SELECT count(*) FROM information_schema.columns " +
                "WHERE table_schema = current_schema() AND table_name = 'cart' AND column_name = 'average_per_member'")
                .singleResult as Long
        LOG.info('information_schema -> Spalten cart.average_per_member: {}', anzahl)

        then:
        anzahl == 0
    }
}
