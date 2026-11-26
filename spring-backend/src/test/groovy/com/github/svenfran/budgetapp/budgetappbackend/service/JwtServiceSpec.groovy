package com.github.svenfran.budgetapp.budgetappbackend.service

import com.github.svenfran.budgetapp.budgetappbackend.config.AuthProperties
import com.github.svenfran.budgetapp.budgetappbackend.helper.DateUtils
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import org.springframework.security.core.userdetails.User as SpringUser
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

import static com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData.REFERENCE_DATE

/**
 * Ausstellung und Ablaufprüfung der Access Tokens über die Clock (Clock.fixed statt Systemzeit), Gültigkeit aus
 * AuthProperties und Session-Id im Claim sid (DIV-65).
 *
 * Alle Zeitpunkte sind sekundengenau: iat/exp haben im JWT Sekundenauflösung. Die Ablauf-Features verwenden Zeitpunkte,
 * bei denen die reale Systemzeit das Gegenteil ergäbe – sie zeigen so, dass der Parser die Clock nutzt.
 */
class JwtServiceSpec extends Specification {

    /** Dummy-Schlüssel (Base64, nur für Tests). */
    static final String SECRET = 'ZGl2dnktand0c2VydmljZXNwZWMtZHVtbXktc2NobHVlc3NlbC1udXItZnVlci10ZXN0cw=='
    /** Anderer Dummy-Schlüssel (Base64) für fremd signierte Tokens. */
    static final String FREMDER_SECRET = 'ZGl2dnktZnJlbWRlci1kdW1teS1zY2hsdWVzc2VsLW51ci1mdWVyLXRlc3Rz'
    static final String EMAIL = 'ben@divvy.test'
    static final Instant JETZT = DateUtils.toDate(REFERENCE_DATE).toInstant()

    def "Token trägt die Ausstellungszeit der Clock und läuft nach der konfigurierten Gültigkeit (#gueltigkeit) ab"() {
        given:
        def service = serviceAm(JETZT, gueltigkeit)

        when:
        def token = service.generateToken(userDetails(EMAIL), 'device-1', 7L)

        then:
        service.extractClaims(token, { Claims c -> c.getIssuedAt() }).toInstant() == JETZT
        service.extractExpiration(token).toInstant() == JETZT + gueltigkeit

        where:
        gueltigkeit << [Duration.ofMinutes(15), Duration.ofMinutes(1)]
    }

    def "Token trägt Subject, DeviceId und Session-Id"() {
        given:
        def service = serviceAm(JETZT)

        when:
        def token = service.generateToken(userDetails(EMAIL), 'device-1', 4711L)

        then:
        service.extractUsername(token) == EMAIL
        service.extractDeviceId(token) == 'device-1'
        service.extractSessionId(token) == 4711L
        service.extractClaims(token, { Claims c -> c.get('sid') }) == '4711'
    }

    def "Token ohne bzw. mit nicht numerischem sid liefert keine Session-Id (#fall)"() {
        given:
        def token = TestData.jwt(secret: SECRET, subject: EMAIL, sessionId: sid,
                expiration: DateUtils.toDate(REFERENCE_DATE.plusDays(1)))

        expect:
        serviceAm(JETZT).extractSessionId(token) == null

        where:
        fall              | sid
        'vor DIV-65'      | null
        'nicht numerisch' | 'abc'
    }

    def "Session-Id lässt sich auch aus einem abgelaufenen, signaturgültigen Token lesen (Logout)"() {
        given:
        def ablauf = DateUtils.toDate(REFERENCE_DATE)
        def token = TestData.jwt(secret: SECRET, subject: EMAIL, sessionId: 42L, expiration: ablauf)
        def service = serviceAm(ablauf.toInstant() + Duration.ofDays(1))

        when:
        service.extractSessionId(token)

        then: 'für die Anmeldung ist das Token abgelaufen'
        thrown(ExpiredJwtException)

        and:
        service.extractSessionIdIgnoringExpiry(token) == Optional.of(42L)
    }

    def "Ohne gültige Signatur bzw. ohne lesbares Token gibt es keine Session-Id (#fall)"() {
        expect:
        serviceAm(JETZT).extractSessionIdIgnoringExpiry(token) == Optional.empty()

        where:
        fall             | token
        'fremd signiert' | TestData.jwt(secret: FREMDER_SECRET, subject: EMAIL, sessionId: 42L, expiration: DateUtils.toDate(REFERENCE_DATE.plusDays(1)))
        'manipuliert'    | manipuliert(TestData.jwt(secret: SECRET, subject: EMAIL, sessionId: 42L, expiration: DateUtils.toDate(REFERENCE_DATE.plusDays(1))))
        'unlesbar'       | 'kein-jwt'
        'leer'           | ''
        'ohne sid'       | TestData.jwt(secret: SECRET, subject: EMAIL, expiration: DateUtils.toDate(REFERENCE_DATE.plusDays(1)))
    }

    def "Token ist laut Clock eine Sekunde vor Ablauf noch gültig, auch wenn der Ablauf real vergangen ist"() {
        given:
        def ablauf = DateUtils.toDate(REFERENCE_DATE.plusDays(21))
        def token = TestData.jwt(secret: SECRET, subject: EMAIL, expiration: ablauf)
        def service = serviceAm(ablauf.toInstant() - Duration.ofSeconds(1))

        expect:
        service.isTokenValid(token, userDetails(EMAIL))
    }

    def "Token wird laut Clock nach Ablauf abgelehnt, auch wenn der Ablauf real in der Zukunft liegt"() {
        given:
        def ablauf = DateUtils.toDate(DateUtils.NO_END_DATE)
        def token = TestData.jwt(secret: SECRET, subject: EMAIL, expiration: ablauf)
        def service = serviceAm(ablauf.toInstant() + Duration.ofSeconds(1))

        when:
        service.isTokenValid(token, userDetails(EMAIL))

        then:
        thrown(ExpiredJwtException)
    }

    private static JwtService serviceAm(Instant jetzt, Duration gueltigkeit = Duration.ofMinutes(15)) {
        new JwtService(SECRET, Clock.fixed(jetzt, ZoneId.systemDefault()),
                new AuthProperties(gueltigkeit, Duration.ofDays(30), Duration.ofDays(90), Duration.ofSeconds(60)))
    }

    /** Ersetzt den Payload durch einen mit anderer sid; die Signatur passt danach nicht mehr. */
    private static String manipuliert(String token) {
        def teile = token.split('\\.')
        def payload = new String(Base64.urlDecoder.decode(teile[1]), 'UTF-8').replace('"42"', '"43"')
        [teile[0], Base64.urlEncoder.withoutPadding().encodeToString(payload.getBytes('UTF-8')), teile[2]].join('.')
    }

    private static userDetails(String username) {
        SpringUser.withUsername(username).password('x').authorities([]).build()
    }
}
