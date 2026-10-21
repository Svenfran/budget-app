package com.github.svenfran.budgetapp.budgetappbackend.testsupport

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.github.svenfran.budgetapp.budgetappbackend.entity.User
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

import static java.nio.charset.StandardCharsets.UTF_8
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
 *
 * Logging (zentral, die Spec loggt Requests nicht selbst; Logger = Spec-Klasse):
 * - INFO je Request: "<METHODE> <URI>[?query] <Anmeldung> -> <Status>", URI-Variablen aufgelöst, z. B.
 *   "GET /api/spendings/13/2025 als User 22 -> 200". Anmeldung: "als User <id>" (user:), "mit Bearer-Token"
 *   (Authorization-Header ohne user:, Wert wird nie geloggt, Identität steht in der Szenario-Zeile der Spec) oder
 *   "ohne Anmeldung". Welche Rolle die User-ID hat, loggt die Szenario-Zeile der Spec.
 * - DEBUG: Request- und Response-Body. Felder, deren Name "token" oder "password" enthält (Groß-/Kleinschreibung
 *   egal, auch verschachtelt), werden als "***" geloggt; ein nicht lesbarer JSON-Body nur als Hinweis.
 *   Header werden nicht geloggt.
 */
trait ApiRequests {

    abstract MockMvc getMockMvc()

    abstract ObjectMapper getObjectMapper()

    /**
     * Führt den Request aus und liefert die Antwort. Named args: user (angemeldeter Nutzer über .with(user(...));
     * weglassen = ohne Anmeldung), body (wird als JSON gesendet). Loggt den Request, siehe Klassendoku.
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
        MvcResult result = mockMvc.perform(request).andReturn()
        logRequest(options, result)
        result.response
    }

    MockHttpServletResponse send(MockHttpServletRequestBuilder request) {
        send([:], request)
    }

    /**
     * JSON-Antwort als Map bzw. List (Zahlen mit == vergleichen, Integer/Long werden numerisch verglichen).
     * Liest die Bytes statt contentAsString: Ohne charset im Content-Type dekodiert MockHttpServletResponse als
     * ISO-8859-1, Jackson schreibt aber UTF-8 (aus "März" würde "MÃ¤rz").
     */
    def json(MockHttpServletResponse response) {
        objectMapper.readValue(response.contentAsByteArray, Object)
    }

    private void logRequest(Map options, MvcResult result) {
        Logger log = LoggerFactory.getLogger(getClass())
        def request = result.request
        def uri = request.queryString ? "${request.requestURI}?${request.queryString}" : request.requestURI
        log.info('{} {} {} -> {}', request.method, uri, anmeldung(options, request.getHeader('Authorization')),
                result.response.status)
        if (log.isDebugEnabled()) {
            if (options.containsKey('body')) {
                log.debug('Request-Body: {}', masked(objectMapper.valueToTree(options.body)))
            }
            log.debug('Response-Body: {}', responseBody(result.response))
        }
    }

    private String anmeldung(Map options, String authorization) {
        if (options.containsKey('user')) {
            options.user instanceof User
                    ? "als User ${((User) options.user).id}"
                    : "als ${((UserDetails) options.user).username}"
        } else if (authorization != null) {
            authorization.startsWith('Bearer ') ? 'mit Bearer-Token' : 'mit Authorization-Header'
        } else {
            'ohne Anmeldung'
        }
    }

    private String responseBody(MockHttpServletResponse response) {
        byte[] bytes = response.contentAsByteArray
        if (!response.contentType?.contains('json')) {
            return new String(bytes, UTF_8)
        }
        try {
            masked(objectMapper.readTree(bytes))
        } catch (IOException ignored) {
            "<kein lesbares JSON, ${bytes.length} Bytes>"
        }
    }

    /** Kopie des Baums, in der Werte von Feldern mit "token"/"password" im Namen durch "***" ersetzt sind. */
    private String masked(JsonNode node) {
        JsonNode copy = node.deepCopy()
        mask(copy)
        copy.toString()
    }

    private void mask(JsonNode node) {
        if (node instanceof ObjectNode) {
            ObjectNode object = (ObjectNode) node
            // for statt each: eine Closure im Trait findet die private Methode mask nicht zuverlässig
            for (String name : object.fieldNames().toList()) {
                def lower = name.toLowerCase()
                if (lower.contains('token') || lower.contains('password')) {
                    object.set(name, new TextNode('***'))
                } else {
                    mask(object.get(name))
                }
            }
        } else if (node?.isArray()) {
            for (JsonNode element : node) {
                mask(element)
            }
        }
    }
}
