package com.github.svenfran.budgetapp.budgetappbackend.validator

import spock.lang.Specification

class EmailValidatorSpec extends Specification {

    def validator = new EmailValidator()

    def "'#email' ist gültig: #gueltig"() {
        expect:
        validator.isValid(email, null) == gueltig

        where:
        email                || gueltig
        'max@example.de'     || true
        'Max@Example.DE'     || true
        '  max@example.de '  || true
        'max example.de'     || false
        'max@ex ample.de'    || false
        ''                   || false
        null                 || false
    }
}
