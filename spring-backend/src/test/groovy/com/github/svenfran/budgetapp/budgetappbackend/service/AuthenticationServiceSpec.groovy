package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationRequest
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.validation.BeanPropertyBindingResult
import spock.lang.Specification

/**
 * Login unter Nutzersperre (DIV-65, B13): Reihenfolge Sperre → Passwortprüfung → Session und der Timing-Schutz bei
 * unbekannter E-Mail (die Prüfung läuft trotzdem, keine Session). Ohne Zeitmessung: geprüft werden die Aufrufe.
 */
class AuthenticationServiceSpec extends Specification {

    UserRepository userRepository = Mock()
    AuthenticationManager authManager = Mock()
    AuthSessionService authSessionService = Mock()
    def service = new AuthenticationService(userRepository, Mock(PasswordEncoder), Mock(JwtService), authManager,
            new VerificationService(Mock(PasswordEncoder), Mock(UserRepository)),
            Mock(GroupService), authSessionService)

    def "Unbekannte E-Mail: Passwortprüfung läuft trotzdem genau einmal, danach BadCredentials und keine Session"() {
        given:
        def request = new AuthenticationRequest(' Niemand@Example.de ', 'geheim123', 'device-1')

        when:
        service.authenticate(request, new BeanPropertyBindingResult(request, 'request'))

        then: 'Sperr-Lookup mit normalisierter E-Mail'
        1 * userRepository.findByEmailForUpdate('niemand@example.de') >> Optional.empty()

        then: 'DaoAuthenticationProvider: Dummy-Prüfung, dann BadCredentials'
        1 * authManager.authenticate({ UsernamePasswordAuthenticationToken t -> t.credentials == 'geheim123' }) >> {
            throw new BadCredentialsException('Bad credentials')
        }

        and:
        0 * authSessionService._
        thrown(BadCredentialsException)
    }

    def "Prüfung erfolgreich, aber kein gesperrter Nutzer (zwischenzeitlich angelegt): keine Session"() {
        given:
        def request = new AuthenticationRequest('neu@example.de', 'geheim123', 'device-1')
        userRepository.findByEmailForUpdate('neu@example.de') >> Optional.empty()
        authManager.authenticate(_) >> { args -> args[0] }

        when:
        service.authenticate(request, new BeanPropertyBindingResult(request, 'request'))

        then:
        0 * authSessionService._
        thrown(BadCredentialsException)
    }

    def "Bekannter Nutzer: erst sperren, dann Passwort prüfen, dann Session anlegen"() {
        given:
        def max = TestData.user(id: 5L, email: 'max@example.de')
        def request = new AuthenticationRequest('Max@Example.de', 'geheim123', 'device-1')

        when:
        service.authenticate(request, new BeanPropertyBindingResult(request, 'request'))

        then:
        1 * userRepository.findByEmailForUpdate('max@example.de') >> Optional.of(max)

        then:
        1 * authManager.authenticate(_) >> { args -> args[0] }

        then:
        1 * authSessionService.createSession(max, 'device-1') >> TestData.issuedSession(userId: 5L)
    }

    def "Falsches Passwort eines bekannten Nutzers: keine Session"() {
        given:
        def request = new AuthenticationRequest('max@example.de', 'falsch', 'device-1')
        userRepository.findByEmailForUpdate('max@example.de') >> Optional.of(TestData.user(id: 5L))
        authManager.authenticate(_) >> { throw new BadCredentialsException('Bad credentials') }

        when:
        service.authenticate(request, new BeanPropertyBindingResult(request, 'request'))

        then:
        0 * authSessionService._
        thrown(BadCredentialsException)
    }
}
