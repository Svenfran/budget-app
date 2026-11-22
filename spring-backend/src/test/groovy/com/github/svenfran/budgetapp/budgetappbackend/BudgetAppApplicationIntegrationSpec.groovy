package com.github.svenfran.budgetapp.budgetappbackend

import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang.management.ManagementFactory

/** DIV-81: Integrations- und API-Specs laufen wie Prod in UTC (Failsafe-{@code argLine}). */
class BudgetAppApplicationIntegrationSpec extends IntegrationSpec {

    private static final Logger LOG = LoggerFactory.getLogger(BudgetAppApplicationIntegrationSpec)

    def "Der Integrationstestlauf startet die JVM mit -Duser.timezone=UTC"() {
        when:
        def startArgumente = ManagementFactory.runtimeMXBean.inputArguments
        def zone = TimeZone.default.ID
        def property = System.getProperty('user.timezone')
        LOG.info('JVM-Startargumente mit Zeitzone: {}; TimeZone.default {}, user.timezone {}',
                startArgumente.findAll { it.contains('timezone') }, zone, property)

        then: 'Startargument der JVM: belegt die Failsafe-argLine unabhängig von der Zone des Rechners'
        startArgumente.contains('-Duser.timezone=UTC')

        and: 'ergänzende Plausibilitätsprüfung'
        property == 'UTC'
        zone == 'UTC'
    }
}
