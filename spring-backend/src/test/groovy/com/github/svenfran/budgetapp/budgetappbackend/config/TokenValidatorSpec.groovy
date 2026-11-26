package com.github.svenfran.budgetapp.budgetappbackend.config

import com.github.svenfran.budgetapp.budgetappbackend.constants.SessionRevokeReason
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.repository.AuthSessionRepository
import com.github.svenfran.budgetapp.budgetappbackend.service.JwtService
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset

import static com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData.REFERENCE_DATE

/**
 * Session-Prüfung des Access Tokens (DIV-65): Das JWT muss über sid auf eine aktive Session desselben Nutzers und
 * derselben DeviceId zeigen. JwtService echt (feste Clock), Nutzer- und Session-Lookup gestubbt.
 */
class TokenValidatorSpec extends Specification {

    /** Dummy-Schlüssel (Base64, nur für Tests). */
    static final String SECRET = 'ZGl2dnktdG9rZW52YWxpZGF0b3JzcGVjLWR1bW15LW51ci1mdWVyLXRlc3Rz'
    static final String DEVICE = 'device-1'
    static final def JETZT = REFERENCE_DATE.atTime(12, 0)

    def clock = Clock.fixed(JETZT.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    def jwtService = new JwtService(SECRET, clock,
            new AuthProperties(Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofDays(90), Duration.ofSeconds(60)))
    def userDetailsService = Stub(UserDetailsService)
    def sessionRepository = Stub(AuthSessionRepository)
    def validator = new TokenValidator(jwtService, userDetailsService, sessionRepository, clock)

    def anna = TestData.user(id: 41L)
    def ben = TestData.user(id: 42L)

    def setup() {
        userDetailsService.loadUserByUsername(anna.email) >> anna
        userDetailsService.loadUserByUsername(ben.email) >> ben
    }

    def "Token einer aktiven Session liefert den Nutzer"() {
        given:
        session(7L, anna)

        expect:
        validator.validate(jwtService.generateToken(anna, DEVICE, 7L), DEVICE).get().is(anna)
    }

    def "Token wird abgelehnt: #fall"() {
        given:
        sessionRepository.findById(7L) >> Optional.ofNullable(sessionDaten == null ? null
                : TestData.authSession([id: 7L, user: anna, deviceId: DEVICE] + sessionDaten))

        expect:
        validator.validate(jwtService.generateToken(anna, DEVICE, 7L), DEVICE).isEmpty()

        where:
        fall                                   | sessionDaten
        'Session existiert nicht'              | null
        'Session widerrufen'                   | [revokedAt: JETZT.minusMinutes(1), revokeReason: SessionRevokeReason.LOGOUT]
        'absolute Grenze genau erreicht'       | [absoluteExpiresAt: JETZT]
        'absolute Grenze überschritten'        | [absoluteExpiresAt: JETZT.minusSeconds(1)]
        'Session für eine andere DeviceId'     | [deviceId: 'device-2']
    }

    def "sid zeigt auf die Session eines anderen Nutzers: abgelehnt"() {
        given:
        session(8L, ben)

        expect:
        validator.validate(jwtService.generateToken(anna, DEVICE, 8L), DEVICE).isEmpty()
    }

    def "JWT ohne sid (vor DIV-65 ausgestellt) wird abgelehnt, auch wenn alles andere passt"() {
        given:
        session(7L, anna)
        def legacy = TestData.jwt(secret: SECRET, subject: anna.email, deviceId: DEVICE,
                expiration: DateUtils.toDate(REFERENCE_DATE.plusDays(1)))

        expect:
        validator.validate(legacy, DEVICE).isEmpty()
    }

    def "DeviceId weicht ab (#fall): abgelehnt"() {
        given:
        session(7L, anna)

        expect:
        validator.validate(jwtService.generateToken(anna, claimDevice, 7L), headerDevice).isEmpty()

        where:
        fall           | claimDevice | headerDevice
        'Header'       | DEVICE      | 'device-2'
        'Header fehlt' | DEVICE      | null
        'Claim'        | 'device-2'  | DEVICE
    }

    def "Nutzer des Subjects existiert nicht mehr: abgelehnt"() {
        given:
        def geloescht = TestData.user(id: 43L)
        userDetailsService.loadUserByUsername(geloescht.email) >> { throw new UsernameNotFoundException('weg') }
        session(9L, geloescht)

        expect:
        validator.validate(jwtService.generateToken(geloescht, DEVICE, 9L), DEVICE).isEmpty()
    }

    def "Abgelaufener Access Token einer aktiven Session wird abgelehnt"() {
        given:
        session(7L, anna)
        def token = TestData.jwt(secret: SECRET, subject: anna.email, deviceId: DEVICE, sessionId: 7L,
                issuedAt: DateUtils.toDate(REFERENCE_DATE), expiration: Date.from(clock.instant().minusSeconds(1)))

        expect:
        validator.validate(token, DEVICE).isEmpty()
    }

    private void session(Long id, user) {
        sessionRepository.findById(id) >> Optional.of(TestData.authSession(id: id, user: user, deviceId: DEVICE))
    }
}
