package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

import com.github.svenfran.budgetapp.budgetappbackend.constants.ApiErrorCode;

import java.util.Map;

/**
 * Für Exceptions, deren Fehlercode von der Instanz abhängt oder die strukturierte Details mitliefern (DIV-108). Hat
 * Vorrang vor der Zuordnung nach Klasse in {@code RestResponseEntityExceptionHandler.ERROR_CODES}.
 */
public interface ApiErrorInfo {

    ApiErrorCode getErrorCode();

    default Map<String, Object> getErrorDetails() {
        return Map.of();
    }
}
