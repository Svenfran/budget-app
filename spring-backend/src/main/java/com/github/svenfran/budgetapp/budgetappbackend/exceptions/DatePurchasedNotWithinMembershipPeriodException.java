package com.github.svenfran.budgetapp.budgetappbackend.exceptions;

import com.github.svenfran.budgetapp.budgetappbackend.constants.ApiErrorCode;

import java.util.Map;

/**
 * Kaufdatum fehlt oder liegt außerhalb der Mitgliedschaft (400). Die Texte sind Legacy-Vertrag (M1), der Code
 * unterscheidet beide Fälle; bei {@link ApiErrorCode#DATE_NOT_WITHIN_MEMBERSHIP} nennt {@code details.userId} die Partei,
 * deren Mitgliedschaft geprüft wurde (DIV-108) – bei Ausgleichszahlungen Zahler oder Empfänger.
 */
public class DatePurchasedNotWithinMembershipPeriodException extends Exception implements ApiErrorInfo {

    private final ApiErrorCode errorCode;
    private final Long userId;

    private DatePurchasedNotWithinMembershipPeriodException(String message, ApiErrorCode errorCode, Long userId) {
        super(message);
        this.errorCode = errorCode;
        this.userId = userId;
    }

    public static DatePurchasedNotWithinMembershipPeriodException required() {
        return new DatePurchasedNotWithinMembershipPeriodException(
                "Date purchased is required", ApiErrorCode.DATE_PURCHASED_REQUIRED, null);
    }

    public static DatePurchasedNotWithinMembershipPeriodException notWithinMembership(Long userId) {
        return new DatePurchasedNotWithinMembershipPeriodException(
                "Date purchased is not within membership period", ApiErrorCode.DATE_NOT_WITHIN_MEMBERSHIP, userId);
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public ApiErrorCode getErrorCode() {
        return errorCode;
    }

    @Override
    public Map<String, Object> getErrorDetails() {
        return userId == null ? Map.of() : Map.of("userId", userId);
    }
}
