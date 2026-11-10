package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.config.ClientIp
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationRequest
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationResponse
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.InvalidEmailException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.MissingDeviceIdException
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.TooManyLoginAttemptsException
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.validation.BeanPropertyBindingResult
import spock.lang.Specification

/**
 * Ablauf des Logins mit Rate-Limit (DIV-64) ohne Datenbank: Reihenfolge Validierung → Reservierung → Passwortprüfung
 * → Freigabe, und was in den Fehlerfällen aufgerufen wird. Limiter und AuthenticationService sind Mocks.
 */
class LoginServiceSpec extends Specification {

    static final ClientIp CLIENT = new ClientIp('203.0.113.7', ClientIp.Source.HEADER)

    LoginRateLimiter rateLimiter = Mock()
    AuthenticationService authenticationService = Mock()
    def service = new LoginService(new VerificationService(Mock(PasswordEncoder), Mock(UserRepository)),
            rateLimiter, authenticationService)

    def request = new AuthenticationRequest('max@example.de', 'geheim123', 'device-1')
    def bindingResult = new BeanPropertyBindingResult(request, 'request')
    def reservation = new LoginReservation('acc', 'ip', 'header', [:])
    def response = new AuthenticationResponse(1L, 'max', 'max@example.de', 0L, 'jwt')

    def "Erfolgreicher Login: reservieren, prüfen, freigeben – in dieser Reihenfolge"() {
        when:
        def ergebnis = service.authenticate(request, bindingResult, CLIENT)

        then:
        1 * rateLimiter.acquire('max@example.de', CLIENT) >> reservation

        then:
        1 * authenticationService.authenticate(request, bindingResult) >> response

        then:
        1 * rateLimiter.recordSuccess(reservation)
        0 * rateLimiter.logFailure(_)
        ergebnis.is(response)
    }

    def "Technischer Fehler beim Freigeben bricht den bereits erfolgreichen Login nicht ab"() {
        given:
        rateLimiter.acquire(_, _) >> reservation
        authenticationService.authenticate(_, _) >> response

        when:
        def ergebnis = service.authenticate(request, bindingResult, CLIENT)

        then:
        1 * rateLimiter.recordSuccess(reservation) >> { throw new DataAccessResourceFailureException('DB weg') }
        noExceptionThrown()
        ergebnis.is(response)
    }

    def "Falsche Zugangsdaten: Fehlversuch wird geloggt, nicht freigegeben, die Exception unverändert weitergeworfen"() {
        given:
        def fehler = new BadCredentialsException('Bad credentials')
        rateLimiter.acquire(_, _) >> reservation
        authenticationService.authenticate(_, _) >> { throw fehler }

        when:
        service.authenticate(request, bindingResult, CLIENT)

        then:
        1 * rateLimiter.logFailure(reservation)
        0 * rateLimiter.recordSuccess(_)
        def geworfen = thrown(BadCredentialsException)
        geworfen.is(fehler)
    }

    def "Abgewiesener Versuch prüft kein Passwort"() {
        given:
        rateLimiter.acquire(_, _) >> { throw new TooManyLoginAttemptsException(900) }

        when:
        service.authenticate(request, bindingResult, CLIENT)

        then:
        0 * authenticationService._
        0 * rateLimiter.recordSuccess(_)
        def e = thrown(TooManyLoginAttemptsException)
        e.retryAfterSeconds == 900
    }

    def "Ungültige Eingabe (#feld) wird vor der Reservierung abgewiesen und zählt nicht"() {
        given:
        bindingResult.rejectValue(feld, 'invalid')

        when:
        service.authenticate(request, bindingResult, CLIENT)

        then:
        0 * rateLimiter._
        0 * authenticationService._
        thrown(erwartet)

        where:
        feld       | erwartet
        'email'    | InvalidEmailException
        'deviceId' | MissingDeviceIdException
    }
}
