package com.github.svenfran.budgetapp.budgetappbackend.constants

import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UnsupportedCurrencyException
import spock.lang.Specification

/**
 * Pinnt die Liste der Gruppenwährungen (DIV-77). Gegenstück im Frontend: {@code src/app/constants/currency.ts} und
 * {@code currency.spec.ts} – eine Änderung hier muss dort von Hand nachgezogen werden.
 */
class SupportedCurrencySpec extends Specification {

    def "Unterstützt genau die zehn Währungen der Story in deren Reihenfolge, Standard ist EUR"() {
        expect:
        SupportedCurrency.values()*.name() == ['EUR', 'USD', 'GBP', 'CHF', 'CAD', 'AUD', 'MXN', 'BRL', 'PLN', 'CZK']
        SupportedCurrency.DEFAULT == SupportedCurrency.EUR
    }

    def "fromCode('#code') liefert #erwartet"() {
        expect:
        SupportedCurrency.fromCode(code) == erwartet

        where:
        code  || erwartet
        'EUR' || SupportedCurrency.EUR
        'CZK' || SupportedCurrency.CZK
        null  || null
    }

    def "fromCode('#code') lehnt nicht unterstützte Codes ab"() {
        when:
        SupportedCurrency.fromCode(code)

        then:
        def e = thrown(UnsupportedCurrencyException)
        e.message == "Currency ${code} is not supported".toString()

        where:
        code << ['JPY', 'usd', '', ' EUR', 'EURO']
    }
}
