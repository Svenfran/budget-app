package com.github.svenfran.budgetapp.budgetappbackend.config

import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessagingException
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import spock.lang.Specification

import java.security.Principal

/**
 * DIV-36 (P1.1–P1.3): Regeln für eingehende STOMP-Frames. Die Token-Prüfung selbst (TokenValidator, dieselbe wie
 * beim REST-Filter) ist gestubbt; hier geht es um Header, Principal-Name, erlaubte Destinations und SEND.
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

    def "CONNECT mit gültigem Token und DeviceId setzt die userId als Principal-Namen"() {
        given:
        def message = frame(StompCommand.CONNECT, [Authorization: 'Bearer ' + JWT, DeviceId: DEVICE])

        when:
        def result = interceptor.preSend(message, channel)

        then: "nicht die E-Mail: NotificationService adressiert Nutzer per userId"
        MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor).user.name == '42'
    }

    def "CONNECT wird abgelehnt: #fall"() {
        given:
        def message = frame(StompCommand.CONNECT, headers)

        when:
        interceptor.preSend(message, channel)

        then:
        thrown(MessagingException)

        where:
        fall                            | headers
        'ohne Header'                   | [:]
        'ohne Bearer-Präfix'            | [Authorization: JWT, DeviceId: DEVICE]
        'Token vom Validator abgelehnt' | [Authorization: 'Bearer jwt-ungueltig', DeviceId: DEVICE]
        'ohne DeviceId'                 | [Authorization: 'Bearer ' + JWT]
        'mit fremder DeviceId'          | [Authorization: 'Bearer ' + JWT, DeviceId: 'device-2']
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
