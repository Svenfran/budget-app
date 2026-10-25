package com.github.svenfran.budgetapp.budgetappbackend.db

import com.github.svenfran.budgetapp.budgetappbackend.repository.TokenRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

/**
 * DIV-46-Migration (Tokens ohne DeviceId löschen, token.device_id NOT NULL). Die Testdatenbank hat das Changeset beim
 * Start auf leeren Tabellen ausgeführt; für den Bestandsfall wird der Zustand davor nachgebaut (Constraint entfernt,
 * Token ohne DeviceId per Native Insert, weil die Entity nullable = false deklariert) und dieselbe SQL-Datei erneut
 * ausgeführt. Postgres-DDL ist transaktional: der Rollback der Test-Transaktion stellt Constraint und Daten wieder her.
 * Die Anweisungen betreffen alle Zeilen, geprüft werden nur die eigenen Tokens.
 */
class TokenDeviceIdNotNullIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(TokenDeviceIdNotNullIntegrationSpec)

    private static final String MIGRATION_SQL =
            '/db/changelog/release-1.x/sql/20261025120000-1_deleteTokensWithoutDeviceIdAndSetNotNull.sql'

    @Autowired TokenRepository tokenRepository

    def "Spalte token.device_id ist NOT NULL"() {
        given:
        LOG.info('Szenario: Schema nach allen Liquibase-Changesets')

        when:
        def nullable = deviceIdNullable()
        LOG.info('information_schema -> token.device_id is_nullable {}', nullable)

        then:
        nullable == 'NO'
    }

    def "Migration löscht Tokens ohne DeviceId und behält die übrigen"() {
        given:
        def alfred = fixtures.user()
        def alfredId = alfred.id
        def mitDeviceId = fixtures.token(alfred, deviceId: 'device-1').id
        flushAndClear()
        entityManager.createNativeQuery('ALTER TABLE token ALTER COLUMN device_id DROP NOT NULL').executeUpdate()
        entityManager.createNativeQuery("INSERT INTO token (token, token_type, expired, revoked, user_id, device_id) " +
                "VALUES ('token-ohne-device', 'BEARER', false, false, ?1, NULL)")
                .setParameter(1, alfredId)
                .executeUpdate()
        def ohneDeviceId = tokenRepository.findByToken('token-ohne-device').get().id
        def nullableVorher = deviceIdNullable()
        flushAndClear()
        LOG.info('Szenario: Nutzer {} mit Token {} (device-1) und Token {} ohne DeviceId, is_nullable vorher {}',
                alfredId, mitDeviceId, ohneDeviceId, nullableVorher)

        when:
        runMigration()
        flushAndClear()
        def tokenIds = tokenRepository.findAllByUserId(alfredId)*.id
        def nullable = deviceIdNullable()
        LOG.info('Migration -> Tokens von Nutzer {}: {}, is_nullable {}', alfredId, tokenIds, nullable)

        then:
        nullableVorher == 'YES'
        tokenIds == [mitDeviceId]
        nullable == 'NO'
    }

    private String deviceIdNullable() {
        entityManager.createNativeQuery("SELECT is_nullable FROM information_schema.columns " +
                "WHERE table_schema = current_schema() AND table_name = 'token' AND column_name = 'device_id'")
                .singleResult
    }

    private void runMigration() {
        def sql = getClass().getResource(MIGRATION_SQL).getText('UTF-8')
        sql.split(';')*.trim().findAll { it }.each {
            entityManager.createNativeQuery(it).executeUpdate()
        }
    }
}
