package com.github.svenfran.budgetapp.budgetappbackend.config

import com.github.svenfran.budgetapp.budgetappbackend.constants.SessionRevokeReason
import com.github.svenfran.budgetapp.budgetappbackend.repository.AuthSessionRepository
import com.github.svenfran.budgetapp.budgetappbackend.service.JwtService
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessagingException
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.userdetails.UserDetailsService
import spock.lang.Specification

import java.security.Principal
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset

/**
 * DIV-36 (P1.1–P1.3): Regeln für eingehende STOMP-Frames. Die Token-Prüfung selbst (TokenValidator, dieselbe wie
 * beim REST-Filter) ist gestubbt; hier geht es um Header, Principal-Name, erlaubte Destinations und SEND. Ausnahme:
 * CONNECT mit dem Token einer widerrufenen Session (DIV-65) läuft über den echten TokenValidator.
 *
 * Destinations stehen als Literale im Test – sie sind der Vertrag mit den Subscriptions im Frontend.
 */
class WebSocketAuthInterceptorSpec extends Specification {

    static final String JWT = 'jwt-1'
    static final String DEVICE = 'device-1'

    def tokenValidator = Stub(TokenValidator) {
        validate(JWT, DEVICE) >> Optional.of(TestData.user(id: 42L))
    }
    def interceptor = new WebSocketAuthInterceptor(tokenValidator)
    def channel = Stub(MessageChannel)
    Principal angemeldet = new UsernamePasswordAuthenticationToken('42', null, [])

    def "#command mit gültigem Token und DeviceId setzt die userId als Principal-Namen"() {
        given:
        def message = frame(command, [Authorization: 'Bearer ' + JWT, DeviceId: DEVICE])

        when:
        def result = interceptor.preSend(message, channel)

        then: "nicht die E-Mail: NotificationService adressiert Nutzer per userId"
        MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor).user.name == '42'

        where: "STOMP ist der gleichwertige Verbindungsbefehl aus STOMP 1.2 (Spring behandelt ihn wie CONNECT)"
        command << [StompCommand.CONNECT, StompCommand.STOMP]
    }

    def "#command wird abgelehnt: #fall"() {
        given:
        def message = frame(command, headers)

        when:
        interceptor.preSend(message, channel)

        then:
        thrown(MessagingException)

        where: "Regression Review F-001: STOMP durfte ohne Anmeldung verbinden"
        command              | fall                            | headers
        StompCommand.CONNECT | 'ohne Header'                   | [:]
        StompCommand.CONNECT | 'ohne Bearer-Präfix'            | [Authorization: JWT, DeviceId: DEVICE]
        StompCommand.CONNECT | 'Token vom Validator abgelehnt' | [Authorization: 'Bearer jwt-ungueltig', DeviceId: DEVICE]
        StompCommand.CONNECT | 'ohne DeviceId'                 | [Authorization: 'Bearer ' + JWT]
        StompCommand.CONNECT | 'mit fremder DeviceId'          | [Authorization: 'Bearer ' + JWT, DeviceId: 'device-2']
        StompCommand.STOMP   | 'ohne Header'                   | [:]
        StompCommand.STOMP   | 'ohne Bearer-Präfix'            | [Authorization: JWT, DeviceId: DEVICE]
        StompCommand.STOMP   | 'Token vom Validator abgelehnt' | [Authorization: 'Bearer jwt-ungueltig', DeviceId: DEVICE]
        StompCommand.STOMP   | 'ohne DeviceId'                 | [Authorization: 'Bearer ' + JWT]
        StompCommand.STOMP   | 'mit fremder DeviceId'          | [Authorization: 'Bearer ' + JWT, DeviceId: 'device-2']
    }

    def "#command mit Token einer #zustand Session: #ergebnis (echter TokenValidator, DIV-65)"() {
        given: 'Dummy-Schlüssel (Base64, nur für Tests); Session 7 von Nutzer 42 auf device-1'
        def jetzt = TestData.REFERENCE_DATE.atTime(12, 0)
        def clock = Clock.fixed(jetzt.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        def jwtService = new JwtService('ZGl2dnktd2Vic29ja2V0c3BlYy1kdW1teS1udXItZnVlci10ZXN0cw==', clock,
                new AuthProperties(Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofDays(90), Duration.ofSeconds(60)))
        def ben = TestData.user(id: 42L)
        def userDetailsService = Stub(UserDetailsService) { loadUserByUsername(ben.email) >> ben }
        def session = TestData.authSession(id: 7L, user: ben, deviceId: DEVICE)
        if (widerrufen) {
            session.revoke(jetzt.minusMinutes(1), SessionRevokeReason.LOGOUT)
        }
        def sessions = Stub(AuthSessionRepository) { findById(7L) >> Optional.of(session) }
        def echterInterceptor = new WebSocketAuthInterceptor(new TokenValidator(jwtService, userDetailsService, sessions, clock))
        def message = frame(command, [Authorization: 'Bearer ' + jwtService.generateToken(ben, DEVICE, 7L), DeviceId: DEVICE])

        when:
        def result = null
        def abgelehnt = false
        try {
            result = echterInterceptor.preSend(message, channel)
        } catch (MessagingException ignored) {
            abgelehnt = true
        }

        then:
        abgelehnt == widerrufen
        widerrufen || MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor).user.name == '42'

        where:
        command              | widerrufen
        StompCommand.CONNECT | false
        StompCommand.CONNECT | true
        StompCommand.STOMP   | true

        zustand = widerrufen ? 'widerrufenen' : 'aktiven'
        ergebnis = widerrufen ? 'abgelehnt' : 'verbunden'
    }

    def "SUBSCRIBE auf #destination ist mit angemeldeter Session erlaubt"() {
        given:
        def message = frame(StompCommand.SUBSCRIBE, [:], destination, angemeldet)

        when:
        def result = interceptor.preSend(message, channel)

        then:
        result.is(message)

        where:
        destination << ['/user/notification/update-group', '/notification/health']
    }

    def "SUBSCRIBE auf #destination wird abgelehnt"() {
        given:
        def message = frame(StompCommand.SUBSCRIBE, [:], destination, angemeldet)

        when:
        interceptor.preSend(message, channel)

        then:
        thrown(MessagingException)

        where:
        destination << [
                '/user/5/notification/update-group',    // fremdes User-Topic (altes Schema)
                '/notification/update-group-user123',   // aufgelöste Session-Destination eines anderen Nutzers
                '/topic/x',
                '/user/notification/',
                null
        ]
    }

    def "SUBSCRIBE ohne angemeldete Session wird abgelehnt"() {
        given:
        def message = frame(StompCommand.SUBSCRIBE, [:], '/user/notification/update-group')

        when:
        interceptor.preSend(message, channel)

        then:
        thrown(MessagingException)
    }

    def "SEND wird immer abgelehnt, auch ein gefälschter Health-Status"() {
        given:
        def message = frame(StompCommand.SEND, [:], '/notification/health', angemeldet)

        when:
        interceptor.preSend(message, channel)

        then:
        thrown(MessagingException)
    }

    def "#command wird ohne Prüfung durchgelassen"() {
        given:
        def message = frame(command, [:])

        when:
        def result = interceptor.preSend(message, channel)

        then:
        result.is(message)

        where:
        command << [StompCommand.UNSUBSCRIBE, StompCommand.DISCONNECT]
    }

    private static Message<byte[]> frame(StompCommand command, Map<String, String> nativeHeaders,
                                         String destination = null, Principal user = null) {
        def accessor = StompHeaderAccessor.create(command)
        nativeHeaders.each { name, value -> accessor.addNativeHeader(name, value) }
        accessor.destination = destination
        accessor.user = user
        accessor.sessionId = 'session-1'
        accessor.leaveMutable = true
        MessageBuilder.createMessage(new byte[0], accessor.messageHeaders)
    }
}
