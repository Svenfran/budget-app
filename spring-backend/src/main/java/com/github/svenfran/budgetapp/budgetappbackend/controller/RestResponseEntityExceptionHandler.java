package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.constants.ApiErrorCode;
import com.github.svenfran.budgetapp.budgetappbackend.dto.ApiErrorDto;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;

import static java.util.Map.entry;


/**
 * Übersetzt die fachlichen Exceptions zentral in HTTP-Antworten. Status über die vier Handler-Listen, Fehlercode über
 * {@link #ERROR_CODES} (DIV-108).
 *
 * Fehlerformat: Mit dem Request-Header {@code X-Error-Format: json} (neue App-Versionen) ist der Body
 * {@link ApiErrorDto} {@code {code, message, details?}}. Ohne den Header bleibt er der bisherige Text der Exception –
 * ältere App-Versionen werten diesen Text aus.
 */
@ControllerAdvice
public class RestResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    private final Logger LOG = LoggerFactory.getLogger(RestResponseEntityExceptionHandler.class);

    /** Request-Header, mit dem ein Client das JSON-Fehlerformat anfordert (auch in den CORS-allowedHeaders). */
    public static final String ERROR_FORMAT_HEADER = "X-Error-Format";
    static final String ERROR_FORMAT_JSON = "json";

    /**
     * Fehlercode je Exception-Klasse; muss jede Klasse der vier Statushandler enthalten (RestResponseEntityExceptionHandlerSpec).
     * Exceptions mit {@link ApiErrorInfo} liefern Code und Details selbst.
     */
    static final Map<Class<? extends Exception>, ApiErrorCode> ERROR_CODES = Map.ofEntries(
            entry(CartNotFoundException.class, ApiErrorCode.CART_NOT_FOUND),
            entry(CategoryNotFoundException.class, ApiErrorCode.CATEGORY_NOT_FOUND),
            entry(UserNotFoundException.class, ApiErrorCode.USER_NOT_FOUND),
            entry(GroupNotFoundException.class, ApiErrorCode.GROUP_NOT_FOUND),
            entry(GroupIdNotFoundException.class, ApiErrorCode.GROUP_NOT_FOUND),
            entry(ShoppingListNotFoundException.class, ApiErrorCode.SHOPPING_LIST_NOT_FOUND),
            entry(ShoppingItemNotFoundException.class, ApiErrorCode.SHOPPING_ITEM_NOT_FOUND),
            entry(ShoppingListDoesNotBelongToGroupException.class, ApiErrorCode.SHOPPING_LIST_NOT_IN_GROUP),
            entry(ShoppingItemDoesNotBelongToShoppingListException.class, ApiErrorCode.SHOPPING_ITEM_NOT_IN_LIST),
            entry(CategoryBelongsNotToGroupException.class, ApiErrorCode.CATEGORY_NOT_IN_GROUP),
            entry(CategoryIsUsedByCartException.class, ApiErrorCode.CATEGORY_IN_USE),
            entry(SettlementNotFoundException.class, ApiErrorCode.SETTLEMENT_NOT_FOUND),
            entry(AddCartCategoryNotFoundException.class, ApiErrorCode.CART_CATEGORY_NOT_FOUND),
            entry(UpdateCartCategoryNotFoundException.class, ApiErrorCode.CART_CATEGORY_NOT_FOUND),
            entry(UserAlreadyExistException.class, ApiErrorCode.EMAIL_ALREADY_EXISTS),
            entry(InvalidEmailException.class, ApiErrorCode.INVALID_EMAIL),
            entry(MissingDeviceIdException.class, ApiErrorCode.DEVICE_ID_REQUIRED),
            entry(UserNameAlreadyExistsException.class, ApiErrorCode.USER_NAME_ALREADY_EXISTS),
            entry(UserIsNotAuthenticatedUser.class, ApiErrorCode.NOT_AUTHENTICATED_USER),
            entry(WrongPasswordException.class, ApiErrorCode.INCORRECT_PASSWORD),
            entry(DatePurchasedNotWithinMembershipPeriodException.class, ApiErrorCode.DATE_NOT_WITHIN_MEMBERSHIP),
            entry(UserNameNotAllowedException.class, ApiErrorCode.USER_NAME_NOT_ALLOWED),
            entry(ShoppingItemMoveWithinSameListException.class, ApiErrorCode.SHOPPING_ITEM_MOVE_WITHIN_SAME_LIST),
            entry(InvalidStatisticsRequestException.class, ApiErrorCode.INVALID_STATISTICS_REQUEST),
            entry(InvalidSettlementPaymentException.class, ApiErrorCode.INVALID_SETTLEMENT_PAYMENT),
            entry(SettlementCategoryNotAllowedException.class, ApiErrorCode.SETTLEMENT_CATEGORY_NOT_ALLOWED),
            entry(UnsupportedCurrencyException.class, ApiErrorCode.UNSUPPORTED_CURRENCY),
            entry(InvalidAmountException.class, ApiErrorCode.INVALID_AMOUNT),
            entry(NotOwnerOfGroupException.class, ApiErrorCode.NOT_GROUP_OWNER),
            entry(MemberAlreadyExistsException.class, ApiErrorCode.MEMBER_ALREADY_EXISTS),
            entry(MemberEqualsOwnerException.class, ApiErrorCode.MEMBER_EQUALS_OWNER),
            entry(NotMemberOfGroupException.class, ApiErrorCode.NOT_GROUP_MEMBER),
            entry(NotOwnerOrMemberOfGroupException.class, ApiErrorCode.NOT_GROUP_OWNER_OR_MEMBER),
            entry(NotOwnerOfCartException.class, ApiErrorCode.NOT_CART_OWNER),
            entry(NotPartyOfSettlementException.class, ApiErrorCode.NOT_SETTLEMENT_PARTY),
            entry(ReceiverChangeNotAllowedException.class, ApiErrorCode.RECEIVER_CHANGE_NOT_ALLOWED),
            entry(SettlementCartNotEditableException.class, ApiErrorCode.SETTLEMENT_CART_NOT_EDITABLE),
            entry(CurrencyChangeConfirmationRequiredException.class, ApiErrorCode.CURRENCY_CHANGE_CONFIRMATION_REQUIRED)
    );

    @ExceptionHandler(value = {
            CartNotFoundException.class,
            CategoryNotFoundException.class,
            UserNotFoundException.class,
            GroupNotFoundException.class,
            GroupIdNotFoundException.class,
            ShoppingListNotFoundException.class,
            ShoppingItemNotFoundException.class,
            ShoppingListDoesNotBelongToGroupException.class,
            ShoppingItemDoesNotBelongToShoppingListException.class,
            CategoryBelongsNotToGroupException.class,
            CategoryIsUsedByCartException.class,
            SettlementNotFoundException.class
    })
    protected ResponseEntity<Object> handleNotFound(Exception ex, WebRequest request) {
        return buildResponse(ex, HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(value = {
            AddCartCategoryNotFoundException.class,
            UpdateCartCategoryNotFoundException.class,
            UserAlreadyExistException.class,
            InvalidEmailException.class,
            MissingDeviceIdException.class,
            UserNameAlreadyExistsException.class,
            UserIsNotAuthenticatedUser.class,
            WrongPasswordException.class,
            DatePurchasedNotWithinMembershipPeriodException.class,
            UserNameNotAllowedException.class,
            ShoppingItemMoveWithinSameListException.class,
            InvalidStatisticsRequestException.class,
            InvalidSettlementPaymentException.class,
            SettlementCategoryNotAllowedException.class,
            UnsupportedCurrencyException.class,
            InvalidAmountException.class
    })
    protected ResponseEntity<Object> handleBadRequest(Exception ex, WebRequest request) {
        return buildResponse(ex, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(value = {
            NotOwnerOfGroupException.class,
            MemberAlreadyExistsException.class,
            MemberEqualsOwnerException.class,
            NotMemberOfGroupException.class,
            NotOwnerOrMemberOfGroupException.class,
            NotOwnerOfCartException.class,
            NotPartyOfSettlementException.class,
            ReceiverChangeNotAllowedException.class
    })
    protected ResponseEntity<Object> handleForbidden(Exception ex, WebRequest request) {
        return buildResponse(ex, HttpStatus.FORBIDDEN, request);
    }

    /**
     * Login-Rate-Limit (DIV-64): 429 mit Retry-After (Sekunden); Body wie alle Fehler je nach X-Error-Format
     * ({@code TOO_MANY_LOGIN_ATTEMPTS} über {@link ApiErrorInfo}) bzw. der feste Text. Nennt nicht, welches Limit
     * gegriffen hat. Das Frontend erkennt den Fall am Status.
     */
    @ExceptionHandler(TooManyLoginAttemptsException.class)
    protected ResponseEntity<Object> handleTooManyLoginAttempts(TooManyLoginAttemptsException ex, WebRequest request) {
        var response = buildResponse(ex, HttpStatus.TOO_MANY_REQUESTS, request);
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(response.getBody());
    }

    /** Gültige Anfrage, die dem Zustand der Ressource widerspricht (DIV-74: Settlement-Cart über normalen Endpoint). */
    @ExceptionHandler(value = {
            SettlementCartNotEditableException.class,
            CurrencyChangeConfirmationRequiredException.class
    })
    protected ResponseEntity<Object> handleConflict(Exception ex, WebRequest request) {
        return buildResponse(ex, HttpStatus.CONFLICT, request);
    }

    /** Name of the unique constraint on user_details.email (Liquibase changeset 20261015120000-1). */
    static final String UNIQUE_USER_EMAIL_CONSTRAINT = "uk_user_details_email";

    // Concurrent registrations/e-mail changes can pass verifyEmailNotExists and only fail at the unique
    // constraint. Other integrity violations keep the default handling (500).
    @ExceptionHandler(DataIntegrityViolationException.class)
    protected ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex, WebRequest request) {
        if (ex.getCause() instanceof ConstraintViolationException cve
                && UNIQUE_USER_EMAIL_CONSTRAINT.equalsIgnoreCase(cve.getConstraintName())) {
            return buildResponse(new UserAlreadyExistException("User with email already exists"), HttpStatus.BAD_REQUEST, request);
        }
        throw ex;
    }

    private ResponseEntity<Object> buildResponse(Exception ex, HttpStatus status, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        if (request == null || !ERROR_FORMAT_JSON.equalsIgnoreCase(request.getHeader(ERROR_FORMAT_HEADER))) {
            // Legacy-Format unverändert (Text, Content-Type per Content-Negotiation wie bisher)
            return new ResponseEntity<>(ex.getMessage(), status);
        }
        var body = ex instanceof ApiErrorInfo info
                ? new ApiErrorDto(info.getErrorCode(), ex.getMessage(), info.getErrorDetails())
                : new ApiErrorDto(ERROR_CODES.get(ex.getClass()), ex.getMessage(), Map.of());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
