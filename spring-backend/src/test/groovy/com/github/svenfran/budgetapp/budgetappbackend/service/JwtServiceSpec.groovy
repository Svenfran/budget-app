package com.github.svenfran.budgetapp.budgetappbackend.service

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
 * Ausstellung und Ablaufprüfung der JWTs über die Clock (Clock.fixed statt Systemzeit).
 *
 * Alle Zeitpunkte sind sekundengenau: iat/exp haben im JWT Sekundenauflösung. Die Ablauf-Features verwenden Zeitpunkte,
 * bei denen die reale Systemzeit das Gegenteil ergäbe – sie zeigen so, dass der Parser die Clock nutzt.
 */
class JwtServiceSpec extends Specification {

    /** Dummy-Schlüssel (Base64, nur für Tests). */
    static final String SECRET = 'ZGl2dnktand0c2VydmljZXNwZWMtZHVtbXktc2NobHVlc3NlbC1udXItZnVlci10ZXN0cw=='
    static final String EMAIL = 'ben@divvy.test'
    static final Instant JETZT = DateUtils.toDate(REFERENCE_DATE).toInstant()

    def "Token trägt die Ausstellungszeit der Clock und läuft 21 Tage später ab"() {
        given:
        def service = serviceAm(JETZT)

        when:
        def token = service.generateToken(userDetails(EMAIL), 'device-1')

        then:
        service.extractClaims(token, { Claims c -> c.getIssuedAt() }).toInstant() == JETZT
        service.extractExpiration(token).toInstant() == JETZT + Duration.ofDays(21)
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

    private static JwtService serviceAm(Instant jetzt) {
        new JwtService(SECRET, Clock.fixed(jetzt, ZoneId.systemDefault()))
    }

    private static userDetails(String username) {
        SpringUser.withUsername(username).password('x').authorities([]).build()
    }
}
