package com.github.svenfran.budgetapp.budgetappbackend

import spock.lang.Specification

import java.lang.management.ManagementFactory

/** DIV-81: JVM-Zeitzone UTC – in der Anwendung ({@code main}) und im Unit-Testlauf (Surefire-{@code argLine}). */
class BudgetAppApplicationSpec extends Specification {

    TimeZone urspruenglicheZone

    def setup() {
        urspruenglicheZone = TimeZone.default
    }

    def cleanup() {
        // genau die gesicherte Zone wiederherstellen, nicht pauschal UTC – sonst könnte eine fehlende argLine verdeckt werden
        TimeZone.default = urspruenglicheZone
    }

    def "Beim Start wird UTC als Default-Zeitzone gesetzt, auch wenn die JVM in Berlin läuft"() {
        given:
        TimeZone.default = TimeZone.getTimeZone('Europe/Berlin')

        when:
        BudgetAppApplication.useUtcAsDefaultTimeZone()

        then:
        TimeZone.default.ID == 'UTC'
    }

    def "Der Unit-Testlauf startet die JVM mit -Duser.timezone=UTC"() {
        expect: 'Startargument der JVM: belegt die Surefire-argLine unabhängig von der Zone des Rechners'
        ManagementFactory.runtimeMXBean.inputArguments.contains('-Duser.timezone=UTC')

        and: 'ergänzende Plausibilitätsprüfung'
        System.getProperty('user.timezone') == 'UTC'
        TimeZone.default.ID == 'UTC'
    }
}
