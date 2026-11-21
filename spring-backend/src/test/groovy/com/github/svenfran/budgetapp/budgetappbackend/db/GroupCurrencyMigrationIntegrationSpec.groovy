package com.github.svenfran.budgetapp.budgetappbackend.db

import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * DIV-77-Migration (group_details.currency_code VARCHAR(3) NOT NULL DEFAULT 'EUR'). Geprüft wird der Default der
 * Datenbank, nicht der Entity-Default: eine Gruppe wird per Native Insert ohne die Spalte angelegt – so wie Postgres beim
 * ADD COLUMN … DEFAULT … NOT NULL die Bestandszeilen füllt. Der Nachweis für echte Bestandsdaten (Beträge unverändert)
 * erfolgt vor dem Merge auf einer Kopie der Dev-DB (siehe PR).
 */
class GroupCurrencyMigrationIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(GroupCurrencyMigrationIntegrationSpec)

    def "Spalte group_details.currency_code ist NOT NULL mit Default EUR"() {
        given:
        LOG.info('Szenario: Schema nach allen Liquibase-Changesets')

        when:
        def spalte = entityManager.createNativeQuery("SELECT is_nullable, column_default, character_maximum_length " +
                "FROM information_schema.columns WHERE table_schema = current_schema() " +
                "AND table_name = 'group_details' AND column_name = 'currency_code'").singleResult as Object[]
        LOG.info('information_schema -> currency_code is_nullable {}, default {}, Länge {}', spalte[0], spalte[1], spalte[2])

        then:
        spalte[0] == 'NO'
        (spalte[1] as String).startsWith("'EUR'")
        spalte[2] == 3
    }

    def "Gruppe ohne Angabe der Währung erhält von der Datenbank EUR"() {
        given:
        def owner = fixtures.user()
        def ownerId = owner.id
        flushAndClear()
        LOG.info('Szenario: Owner {}; Insert in group_details ohne currency_code', ownerId)

        when:
        def groupId = entityManager.createNativeQuery("INSERT INTO group_details (name, date_created, owner_id) " +
                "VALUES ('Altgruppe', now(), ?1) RETURNING id").setParameter(1, ownerId).singleResult as Long
        def waehrung = entityManager.createNativeQuery('SELECT currency_code FROM group_details WHERE id = ?1')
                .setParameter(1, groupId).singleResult
        LOG.info('Native Insert -> Gruppe {}, currency_code {}', groupId, waehrung)

        then:
        waehrung == 'EUR'
    }

    def "Protokolltabelle group_currency_change löscht ihre Einträge mit der Gruppe (ON DELETE CASCADE)"() {
        given:
        LOG.info('Szenario: Schema nach allen Liquibase-Changesets')

        when:
        def deleteRule = entityManager.createNativeQuery("SELECT rc.delete_rule FROM information_schema.referential_constraints rc " +
                "WHERE rc.constraint_schema = current_schema() AND rc.constraint_name = 'fk_group_currency_change_group'").singleResult
        LOG.info('information_schema -> fk_group_currency_change_group delete_rule {}', deleteRule)

        then:
        deleteRule == 'CASCADE'
    }
}
