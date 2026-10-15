package com.github.svenfran.budgetapp.budgetappbackend.testsupport

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user

/**
 * Request-Helfer für API-Specs (*ApiSpec): class XyzControllerApiSpec extends IntegrationSpec implements ApiRequests.
 * mockMvc kommt aus IntegrationSpec, objectMapper deklariert die Spec per @Autowired (kein Kontext-Effekt).
 *
 * <pre>
 * def response = send(put('/api/userprofile/update-username'), user: anna, body: [id: anna.id, userName: 'Ben'])
 * response.status == 400
 * json(response).userEmail == ...
 * </pre>
 *
 * URLs:
 * - Als String-Literal im Test, nicht aus Konstanten des Produktionscodes: Sie sind Teil des Vertrags mit dem
 *   Frontend, eine Umbenennung im Backend soll den Test scheitern lassen.
 * - Pfad-Variablen über die URI-Variablen von MockMvc (get('/api/carts/{id}', id)), nicht per String-Verkettung.
 * - Wird ein Endpunkt in einer Spec mehrfach aufgerufen, kapselt ihn ein privater Helfer der Spec
 *   (z. B. changeEmail(user, email)).
 */
trait ApiRequests {

    abstract MockMvc getMockMvc()

    abstract ObjectMapper getObjectMapper()

    /**
     * Führt den Request aus und liefert die Antwort. Named args: user (angemeldeter Nutzer über .with(user(...));
     * weglassen = ohne Anmeldung), body (wird als JSON gesendet).
     */
    MockHttpServletResponse send(Map options, MockHttpServletRequestBuilder request) {
        assert ['user', 'body'].containsAll(options.keySet()): "Unbekannte Option: ${options.keySet()}"
        if (options.containsKey('user')) {
            // user: null würde still ohne Anmeldung laufen (403) und Rechte-Tests zufällig grün machen
            assert options.user != null: 'user: ist null; für Requests ohne Anmeldung die Option weglassen'
            request.with(user((UserDetails) options.user))
        }
        if (options.containsKey('body')) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(options.body))
        }
        mockMvc.perform(request).andReturn().response
    }

    MockHttpServletResponse send(MockHttpServletRequestBuilder request) {
        send([:], request)
    }

    /** JSON-Antwort als Map bzw. List (Zahlen mit == vergleichen, Integer/Long werden numerisch verglichen). */
    def json(MockHttpServletResponse response) {
        objectMapper.readValue(response.contentAsString, Object)
    }
}
