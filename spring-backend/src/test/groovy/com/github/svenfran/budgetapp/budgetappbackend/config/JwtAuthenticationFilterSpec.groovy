package com.github.svenfran.budgetapp.budgetappbackend.config

import com.github.svenfran.budgetapp.budgetappbackend.repository.TokenRepository
import com.github.svenfran.budgetapp.budgetappbackend.service.JwtService
import com.github.svenfran.budgetapp.budgetappbackend.testsupport.TestData
import io.jsonwebtoken.ExpiredJwtException
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import spock.lang.Specification

import javax.servlet.FilterChain

/**
 * DIV-18: Fälle des JWT-Filters, die sich über HTTP nicht deterministisch herstellen lassen (Ende-zu-Ende-Fälle:
 * AuthenticationControllerApiSpec).
 *
 * JwtService wird hier abweichend von der Mock-Konvention gestubbt: Ein JWT, das erst zwischen dem ersten Parsen und
 * isTokenValid abläuft, lässt sich mit dem echten JwtService nicht ohne Wartezeit erzeugen.
 */
class JwtAuthenticationFilterSpec extends Specification {

    static final String JWT = 'jwt-1'
    static final String EMAIL = 'alfred@divvy.test'
    static final String DEVICE = 'device-1'

    def jwtService = Stub(JwtService)
    def userDetailsService = Stub(UserDetailsService)
    def tokenRepository = Stub(TokenRepository)
    def chain = Mock(FilterChain)
    def filter = new JwtAuthenticationFilter(jwtService, userDetailsService, tokenRepository)
    def request = new MockHttpServletRequest()
    def response = new MockHttpServletResponse()

    def setup() {
        def alfred = TestData.user(email: EMAIL)
        userDetailsService.loadUserByUsername(EMAIL) >> alfred
        tokenRepository.findByToken(JWT) >> Optional.of(TestData.token(user: alfred, token: JWT, deviceId: DEVICE))
        jwtService.extractUsername(JWT) >> EMAIL
        jwtService.extractDeviceId(JWT) >> DEVICE

        request.addHeader('Authorization', JwtService.BEARER_PREFIX + JWT)
        request.addHeader('deviceId', DEVICE)
    }

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    def "Läuft das JWT erst während der Prüfung ab, geht der Request ohne Anmeldung weiter"() {
        given:
        jwtService.isTokenValid(JWT, _) >> { throw new ExpiredJwtException(null, null, 'abgelaufen') }

        when:
        filter.doFilter(request, response, chain)

        then:
        noExceptionThrown()
        1 * chain.doFilter(request, response)
        SecurityContextHolder.context.authentication == null
    }

    def "Exceptions aus der nachgelagerten Filterkette werden nicht verschluckt"() {
        given:
        jwtService.isTokenValid(JWT, _) >> true

        when:
        filter.doFilter(request, response, chain)

        then:
        1 * chain.doFilter(request, response) >> { throw new IllegalArgumentException('nachgelagert') }
        thrown(IllegalArgumentException)
    }
}
