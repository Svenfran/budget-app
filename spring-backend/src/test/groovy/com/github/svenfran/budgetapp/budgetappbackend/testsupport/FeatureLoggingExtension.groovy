package com.github.svenfran.budgetapp.budgetappbackend.testsupport

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.spockframework.runtime.AbstractRunListener
import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.IterationInfo
import org.spockframework.runtime.model.SpecInfo

/**
 * Loggt vor jeder Iteration jeder Spec (Unit, Integration, API) deren Namen auf INFO: ">> <Name>".
 * Logger ist die Spec-Klasse, die Zeile erscheint also wie die spec-eigenen Zeilen.
 *
 * - Registriert als globale Extension in src/test/resources/META-INF/services/
 *   org.spockframework.runtime.extension.IGlobalExtension; Specs müssen nichts tun.
 * - Läuft vor setup(): die Zeile steht vor allem, was das Feature selbst loggt.
 * - Datengetriebene Features loggen je Iteration den aufgelösten Namen (#param).
 * - Der Name wird ungefiltert geloggt: Feature-Namen dürfen keine Secrets (Tokens, Passwörter) als #param
 *   enthalten – stattdessen ein fachliches Merkmal (#gefunden, #fall).
 */
class FeatureLoggingExtension implements IGlobalExtension {

    @Override
    void visitSpec(SpecInfo spec) {
        Logger log = LoggerFactory.getLogger(spec.bottomSpec.reflection)
        spec.addListener(new AbstractRunListener() {
            @Override
            void beforeIteration(IterationInfo iteration) {
                log.info('>> {}', iteration.displayName)
            }
        })
    }
}
