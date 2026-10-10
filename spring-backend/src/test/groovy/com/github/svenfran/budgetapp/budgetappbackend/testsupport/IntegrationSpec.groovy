package com.github.svenfran.budgetapp.budgetappbackend.testsupport

import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import com.github.svenfran.budgetapp.budgetappbackend.service.NotificationService
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Transactional
import spock.lang.Specification

import javax.persistence.EntityManager

/**
 * Gemeinsame Basis aller Integrations- und API-Specs (ein Container, ein Spring-Kontext).
 *
 * Konventionen:
 * - Suffix *IntegrationSpec (Service + DB) bzw. *ApiSpec (MockMvc); nur diese laufen in Failsafe (./mvnw verify).
 *   Reine Unit-Specs (*Spec) erben NICHT von dieser Klasse und laufen ohne Docker in Surefire (./mvnw test).
 * - Keine zusätzliche Kontext-Konfiguration in einzelnen Specs (@SpringBean, @MockBean, @TestPropertySource,
 *   @DirtiesContext, eigenes @SpringBootTest): jede erzeugt einen weiteren Kontext (~26 s).
 *   Braucht eine Spec einen Mock, wird er hier ergänzt.
 * - Kein withReuse(true) am Container (siehe PostgresTestContainer).
 * - NotificationService ist gemockt: Benachrichtigungen als Interaktion prüfen,
 *   z. B. 1 * notificationService.sendGroupUpdateNotification(groupId, _).
 *
 * Isolation:
 * - Standard: Rollback über das @Transactional dieser Klasse; Service und Test teilen einen Persistence Context.
 * - Commit-abhängige Specs (Scheduler, Sichtbarkeit nach Commit, Lazy-Loading): @Transactional(propagation =
 *   Propagation.NOT_SUPPORTED) am Spec und databaseCleaner.truncateAll() in setup(), also VOR jedem Feature räumen.
 * - Rollback-Specs fragen nur Daten ihrer eigenen Testdaten ab (z. B. per Gruppen-ID), nicht findAll()/count():
 *   Commit-Specs können Restdaten hinterlassen.
 * - Nach flushAndClear() IDs vergleichen, nicht Objekte (die Entities haben kein eigenes equals).
 * - Kein deleteAll() in bestimmter Reihenfolge als Setup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
abstract class IntegrationSpec extends Specification {

    @Autowired MockMvc mockMvc
    @Autowired EntityManager entityManager

    @SpringBean
    NotificationService notificationService = Mock()

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestContainer.INSTANCE::getJdbcUrl)
        registry.add("spring.datasource.username", PostgresTestContainer.INSTANCE::getUsername)
        registry.add("spring.datasource.password", PostgresTestContainer.INSTANCE::getPassword)
    }

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    /** Setzt den Nutzer direkt in den SecurityContext (DataLoaderService.getAuthenticatedUser() lädt ihn per E-Mail). */
    protected void loginAs(User user) {
        SecurityContextHolder.context.authentication =
                new UsernamePasswordAuthenticationToken(user, null, user.authorities)
    }

    /** Schreibt ausstehendes SQL und leert den Persistence Context, damit danach gegen die DB geprüft wird. */
    protected void flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }
}
