package com.github.svenfran.budgetapp.budgetappbackend.config

import com.github.svenfran.budgetapp.budgetappbackend.service.AuthSessionService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.support.CronExpression
import spock.lang.Specification

/**
 * Verdrahtung des Session-Cleanups (DIV-122): Im Test-Profil ist Scheduling aus, der Cron-Platzhalter wird dort also nie
 * aufgelöst. Ein falscher Property-Name fiele sonst erst beim Start in dev bzw. prod auf (Startabbruch).
 */
class SessionCleanupScheduleSpec extends Specification {

    def "deleteEndedSessions läuft per Cron aus der Property task.sessionCleanup.schedule"() {
        when:
        def scheduled = AuthSessionService.getMethod('deleteEndedSessions').getAnnotation(Scheduled)

        then:
        scheduled != null
        scheduled.cron() == '${task.sessionCleanup.schedule}'
    }

    def "#datei setzt task.sessionCleanup.schedule als gültigen Cron und nicht mehr task.clearToken.schedule"() {
        given:
        def properties = new Properties()
        getClass().getResourceAsStream("/$datei").withCloseable { properties.load(it) }

        when:
        def cron = properties.getProperty('task.sessionCleanup.schedule')
        CronExpression.parse(cron)

        then:
        noExceptionThrown()
        cron == erwartet
        !properties.containsKey('task.clearToken.schedule')

        where:
        datei                          || erwartet
        'application.properties'       || '0 0 * * * *'
        'application-prod.properties'  || '0 0 0 * * ?'
    }
}
