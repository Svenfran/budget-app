package com.github.svenfran.budgetapp.budgetappbackend.helper

import com.github.svenfran.budgetapp.budgetappbackend.testsupport.IntegrationSpec
import org.springframework.beans.factory.annotation.Autowired

class TranslatorIntegrationSpec extends IntegrationSpec {

    @Autowired Translator translator

    def "Translator übersetzt '#key' in #locale"() {
        expect:
        translator.translate(key, args as Object[], locale) == expectedMessage

        where:
        key                             | args       | locale           || expectedMessage
        "email.reset.password.subject"  | []         | Locale.GERMAN    || "Divvy - Dein temporäres Passwort"
        "email.reset.password.subject"  | []         | Locale.ENGLISH   || "Divvy - Your temporary password"
        "email.reset.password.subject"  | []         | new Locale("es") || "Divvy - Su contraseña temporal"
        "email.reset.password.greeting" | ["Max"]    | Locale.GERMAN    || "Hallo Max,"
        "email.reset.password.greeting" | ["John"]   | Locale.ENGLISH   || "Hello John,"
        "email.reset.password.greeting" | ["Carlos"] | new Locale("es") || "Hola Carlos,"
    }
}
