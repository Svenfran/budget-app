package com.github.svenfran.budgetapp.budgetappbackend.controller

import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import spock.lang.Specification

import java.sql.SQLException

class RestResponseEntityExceptionHandlerSpec extends Specification {

    def handler = new RestResponseEntityExceptionHandler()

    def "Verletzung des E-Mail-Unique-Constraints (parallele Requests) wird zu 400 'already exists'"() {
        given:
        def ex = violation('uk_user_details_email')

        when:
        def response = handler.handleDataIntegrityViolation(ex, null)

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
        response.body == 'User with email already exists'
    }

    def "Andere Integritätsverletzungen werden nicht umgedeutet"() {
        given:
        def ex = violation(constraint)

        when:
        handler.handleDataIntegrityViolation(ex, null)

        then:
        def thrownEx = thrown(DataIntegrityViolationException)
        thrownEx.is(ex)

        where:
        constraint << ['fk_cart_user', null]
    }

    private static DataIntegrityViolationException violation(String constraintName) {
        new DataIntegrityViolationException('could not execute statement',
                new ConstraintViolationException('duplicate key', new SQLException('duplicate key'), constraintName))
    }
}
