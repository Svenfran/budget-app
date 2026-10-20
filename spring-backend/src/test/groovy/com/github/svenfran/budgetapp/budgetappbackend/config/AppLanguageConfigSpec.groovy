package com.github.svenfran.budgetapp.budgetappbackend.config

import org.springframework.mock.web.MockHttpServletRequest
import spock.lang.Specification

/**
 * DIV-30: Die Request-Locale ist auf die unterstützten Sprachen de/en/es begrenzt, Standard de.
 */
class AppLanguageConfigSpec extends Specification {

    def resolver = new AppLanguageConfig().localeResolver()

    def "Accept-Language '#header' ergibt die Sprache '#expected'"() {
        given:
        def request = new MockHttpServletRequest()
        if (header) {
            request.addHeader('Accept-Language', header)
        }

        expect:
        resolver.resolveLocale(request).toLanguageTag() == expected

        where:
        header         || expected
        null           || 'de'
        'de'           || 'de'
        'en'           || 'en'
        'es'           || 'es'
        'en-US'        || 'en'
        'es-MX'        || 'es'
        'fr'           || 'de'
        'fr, en;q=0.8' || 'en'
    }
}
