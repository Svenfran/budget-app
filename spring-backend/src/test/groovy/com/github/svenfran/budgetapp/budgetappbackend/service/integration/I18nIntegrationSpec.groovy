package com.github.svenfran.budgetapp.budgetappbackend.service.integration

import com.github.svenfran.budgetapp.budgetappbackend.helper.Translator
import com.github.svenfran.budgetapp.budgetappbackend.service.container.TestContainerEnv
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import spock.lang.Unroll

@Transactional
class I18nIntegrationSpec extends TestContainerEnv {

    @Autowired Translator translator

    def "all message bundles are consistent across languages"() {
        given:
        def base = loadMessages("messages")
        def de = loadMessages("messages")
        def en = loadMessages("messages_en")
        def es = loadMessages("messages_es")

        expect: "all languages have the same keys"
        de.keySet() == base.keySet()
        en.keySet() == base.keySet()
        es.keySet() == base.keySet()

        and: "no duplicate keys exist"
        [base, de, en, es].each { bundle ->
            assert bundle.size() == bundle.keySet().size()
        }
    }

    @Unroll
    def "translator returns correct message for key '#key' in locale #locale"() {
        when:
        def message = translator.translate(key, args as Object[], locale)

        then:
        message != null && !message.isBlank()
        message == expectedMessage

        where:
        key                                 | args            | locale             | expectedMessage
        "email.reset.password.subject"      | []              | Locale.GERMAN      | "Divvy - Dein temporäres Passwort"
        "email.reset.password.subject"      | []              | Locale.ENGLISH     | "Divvy - Your temporary password"
        "email.reset.password.subject"      | []              | new Locale("es")   | "Divvy - Su contraseña temporal"

        "email.reset.password.greeting"     | ["Max"]         | Locale.GERMAN      | "Hallo Max,"
        "email.reset.password.greeting"     | ["John"]        | Locale.ENGLISH     | "Hello John,"
        "email.reset.password.greeting"     | ["Carlos"]      | new Locale("es")   | "Hola Carlos,"
    }

    // ---- Hilfsmethode zum Laden von messages_*.properties ----
    private static Map<String, String> loadMessages(String bundleName) {
        def props = new Properties()
        def resource = I18nIntegrationSpec.classLoader.getResourceAsStream("i18n/${bundleName}.properties")
                ?: I18nIntegrationSpec.classLoader.getResourceAsStream("${bundleName}.properties")
        if (resource == null) throw new IllegalStateException("File not found: ${bundleName}.properties")
        props.load(resource)
        props as Map<String, String>
    }

}
