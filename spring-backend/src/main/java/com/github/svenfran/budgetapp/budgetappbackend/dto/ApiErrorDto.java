package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.svenfran.budgetapp.budgetappbackend.constants.ApiErrorCode;

import java.util.Map;

/**
 * Fehlerbody für Clients mit {@code X-Error-Format: json} (DIV-108). {@code message} ist der bisherige Text der
 * Exception, {@code details} entfällt im JSON, wenn leer (z. B. {@code {"userId": 42}} bei
 * {@link ApiErrorCode#DATE_NOT_WITHIN_MEMBERSHIP}).
 */
public record ApiErrorDto(
        ApiErrorCode code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> details) {
}
