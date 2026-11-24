package com.github.svenfran.budgetapp.budgetappbackend.constants;

/**
 * Maschinenlesbare Fehlercodes der API (DIV-108). Clients, die {@code X-Error-Format: json} senden, bekommen sie im
 * Fehlerbody {@code {code, message, details?}}. Die Namen sind Teil des Vertrags mit dem Frontend: Umbenennen nur
 * zusammen mit dem Frontend. Zuordnung Exception → Code: {@code RestResponseEntityExceptionHandler.ERROR_CODES}.
 */
public enum ApiErrorCode {

    // 404
    CART_NOT_FOUND,
    CATEGORY_NOT_FOUND,
    USER_NOT_FOUND,
    GROUP_NOT_FOUND,
    SHOPPING_LIST_NOT_FOUND,
    SHOPPING_ITEM_NOT_FOUND,
    SHOPPING_LIST_NOT_IN_GROUP,
    SHOPPING_ITEM_NOT_IN_LIST,
    CATEGORY_NOT_IN_GROUP,
    CATEGORY_IN_USE,
    SETTLEMENT_NOT_FOUND,

    // 400
    CART_CATEGORY_NOT_FOUND,
    EMAIL_ALREADY_EXISTS,
    INVALID_EMAIL,
    DEVICE_ID_REQUIRED,
    USER_NAME_ALREADY_EXISTS,
    NOT_AUTHENTICATED_USER,
    INCORRECT_PASSWORD,
    DATE_NOT_WITHIN_MEMBERSHIP,
    DATE_PURCHASED_REQUIRED,
    USER_NAME_NOT_ALLOWED,
    SHOPPING_ITEM_MOVE_WITHIN_SAME_LIST,
    INVALID_STATISTICS_REQUEST,
    INVALID_SETTLEMENT_PAYMENT,
    SETTLEMENT_CATEGORY_NOT_ALLOWED,
    UNSUPPORTED_CURRENCY,
    INVALID_AMOUNT,

    // 403
    NOT_GROUP_OWNER,
    MEMBER_ALREADY_EXISTS,
    MEMBER_EQUALS_OWNER,
    NOT_GROUP_MEMBER,
    NOT_GROUP_OWNER_OR_MEMBER,
    NOT_CART_OWNER,
    NOT_SETTLEMENT_PARTY,
    RECEIVER_CHANGE_NOT_ALLOWED,

    // 409
    SETTLEMENT_CART_NOT_EDITABLE,
    CURRENCY_CHANGE_CONFIRMATION_REQUIRED,

    // 429
    /** Login-Rate-Limit (DIV-64); die Restzeit steht im Header {@code Retry-After}. */
    TOO_MANY_LOGIN_ATTEMPTS
}
