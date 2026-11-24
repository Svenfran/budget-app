package com.github.svenfran.budgetapp.budgetappbackend.controller

import com.github.svenfran.budgetapp.budgetappbackend.constants.ApiErrorCode
import com.github.svenfran.budgetapp.budgetappbackend.dto.ApiErrorDto
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import spock.lang.Specification

import java.sql.SQLException

import static com.github.svenfran.budgetapp.budgetappbackend.constants.ApiErrorCode.*

/**
 * Exception-Mapping ohne Spring-Kontext: Fehlercodes (DIV-108), Header-Weiche zwischen Legacy-Text und JSON-Format und
 * der Sonderfall Unique-Constraint. HTTP-Serialisierung und Content-Type prüfen die API-Specs.
 */
class RestResponseEntityExceptionHandlerSpec extends Specification {

    /** Die vier fachlichen Statushandler; handleDataIntegrityViolation und geerbte Handler gehören nicht dazu. */
    static final List<String> STATUS_HANDLERS = ['handleNotFound', 'handleBadRequest', 'handleForbidden', 'handleConflict']

    def handler = new RestResponseEntityExceptionHandler()

    def "Jede Exception der vier Statushandler hat einen Fehlercode, und es gibt keine Codes für ungemappte Klassen"() {
        when:
        def gemappt = STATUS_HANDLERS.collectMany { name ->
            RestResponseEntityExceptionHandler.getDeclaredMethod(name, Exception, WebRequest)
                    .getAnnotation(ExceptionHandler).value().toList()
        } as Set

        then:
        gemappt.size() == 38
        RestResponseEntityExceptionHandler.ERROR_CODES.keySet() == gemappt
    }

    def "#exception.simpleName hat den Code #code"() {
        expect:
        RestResponseEntityExceptionHandler.ERROR_CODES[exception] == code

        where:
        exception                                        || code
        CartNotFoundException                            || CART_NOT_FOUND
        CategoryNotFoundException                        || CATEGORY_NOT_FOUND
        UserNotFoundException                            || USER_NOT_FOUND
        GroupNotFoundException                           || GROUP_NOT_FOUND
        GroupIdNotFoundException                         || GROUP_NOT_FOUND
        ShoppingListNotFoundException                    || SHOPPING_LIST_NOT_FOUND
        ShoppingItemNotFoundException                    || SHOPPING_ITEM_NOT_FOUND
        ShoppingListDoesNotBelongToGroupException        || SHOPPING_LIST_NOT_IN_GROUP
        ShoppingItemDoesNotBelongToShoppingListException || SHOPPING_ITEM_NOT_IN_LIST
        CategoryBelongsNotToGroupException               || CATEGORY_NOT_IN_GROUP
        CategoryIsUsedByCartException                    || CATEGORY_IN_USE
        SettlementNotFoundException                      || SETTLEMENT_NOT_FOUND
        AddCartCategoryNotFoundException                 || CART_CATEGORY_NOT_FOUND
        UpdateCartCategoryNotFoundException              || CART_CATEGORY_NOT_FOUND
        UserAlreadyExistException                        || EMAIL_ALREADY_EXISTS
        InvalidEmailException                            || INVALID_EMAIL
        MissingDeviceIdException                         || DEVICE_ID_REQUIRED
        UserNameAlreadyExistsException                   || USER_NAME_ALREADY_EXISTS
        UserIsNotAuthenticatedUser                       || NOT_AUTHENTICATED_USER
        WrongPasswordException                           || INCORRECT_PASSWORD
        DatePurchasedNotWithinMembershipPeriodException  || DATE_NOT_WITHIN_MEMBERSHIP
        UserNameNotAllowedException                      || USER_NAME_NOT_ALLOWED
        ShoppingItemMoveWithinSameListException          || SHOPPING_ITEM_MOVE_WITHIN_SAME_LIST
        InvalidStatisticsRequestException                || INVALID_STATISTICS_REQUEST
        InvalidSettlementPaymentException                || INVALID_SETTLEMENT_PAYMENT
        SettlementCategoryNotAllowedException            || SETTLEMENT_CATEGORY_NOT_ALLOWED
        UnsupportedCurrencyException                     || UNSUPPORTED_CURRENCY
        InvalidAmountException                           || INVALID_AMOUNT
        NotOwnerOfGroupException                         || NOT_GROUP_OWNER
        MemberAlreadyExistsException                     || MEMBER_ALREADY_EXISTS
        MemberEqualsOwnerException                       || MEMBER_EQUALS_OWNER
        NotMemberOfGroupException                        || NOT_GROUP_MEMBER
        NotOwnerOrMemberOfGroupException                 || NOT_GROUP_OWNER_OR_MEMBER
        NotOwnerOfCartException                          || NOT_CART_OWNER
        NotPartyOfSettlementException                    || NOT_SETTLEMENT_PARTY
        ReceiverChangeNotAllowedException                || RECEIVER_CHANGE_NOT_ALLOWED
        SettlementCartNotEditableException               || SETTLEMENT_CART_NOT_EDITABLE
        CurrencyChangeConfirmationRequiredException      || CURRENCY_CHANGE_CONFIRMATION_REQUIRED
    }

    def "Ohne bzw. mit unbekanntem X-Error-Format (#header) bleibt der Body der bisherige Text"() {
        when:
        def response = handler.handleForbidden(new MemberAlreadyExistsException('Member already exists'), request(header))

        then:
        response.statusCode == HttpStatus.FORBIDDEN
        response.body == 'Member already exists'
        response.headers.getContentType() == null

        where:
        header << [null, 'xml', '']
    }

    def "Mit X-Error-Format '#header' ist der Body {code, message} als JSON"() {
        when:
        def response = handler.handleForbidden(new MemberAlreadyExistsException('Member already exists'), request(header))

        then:
        response.statusCode == HttpStatus.FORBIDDEN
        response.headers.getContentType() == MediaType.APPLICATION_JSON
        response.body == new ApiErrorDto(MEMBER_ALREADY_EXISTS, 'Member already exists', [:])

        where:
        header << ['json', 'JSON']
    }

    def "ApiErrorInfo der Exception hat Vorrang vor der Zuordnung nach Klasse"() {
        given:
        def ex = new MitInfo('Date purchased is not within membership period')

        when:
        def response = handler.handleBadRequest(ex, request('json'))

        then:
        response.body == new ApiErrorDto(DATE_NOT_WITHIN_MEMBERSHIP, 'Date purchased is not within membership period', [userId: 42L])
    }

    def "Verletzung des E-Mail-Unique-Constraints (parallele Requests) wird zu 400 'already exists'"() {
        given:
        def ex = violation('uk_user_details_email')

        when:
        def response = handler.handleDataIntegrityViolation(ex, null)

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
        response.body == 'User with email already exists'
    }

    def "Verletzung des E-Mail-Unique-Constraints mit X-Error-Format json: Code EMAIL_ALREADY_EXISTS"() {
        when:
        def response = handler.handleDataIntegrityViolation(violation('uk_user_details_email'), request('json'))

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
        response.body == new ApiErrorDto(EMAIL_ALREADY_EXISTS, 'User with email already exists', [:])
    }

    def "Andere Integritätsverletzungen werden nicht umgedeutet (Constraint #constraint, X-Error-Format #header)"() {
        given:
        def ex = violation(constraint)

        when:
        handler.handleDataIntegrityViolation(ex, header == null ? null : request(header))

        then:
        def thrownEx = thrown(DataIntegrityViolationException)
        thrownEx.is(ex)

        where:
        constraint     | header
        'fk_cart_user' | null
        null           | null
        'fk_cart_user' | 'json'
        null           | 'json'
    }

    private static WebRequest request(String errorFormat) {
        def servletRequest = new MockHttpServletRequest()
        if (errorFormat != null) {
            servletRequest.addHeader('X-Error-Format', errorFormat)
        }
        new ServletWebRequest(servletRequest)
    }

    private static DataIntegrityViolationException violation(String constraintName) {
        new DataIntegrityViolationException('could not execute statement',
                new ConstraintViolationException('duplicate key', new SQLException('duplicate key'), constraintName))
    }

    /** Exception mit eigenem Code und Details, unabhängig von ERROR_CODES. */
    static class MitInfo extends Exception implements ApiErrorInfo {
        MitInfo(String message) { super(message) }

        @Override
        ApiErrorCode getErrorCode() { DATE_NOT_WITHIN_MEMBERSHIP }

        @Override
        Map<String, Object> getErrorDetails() { [userId: 42L] }
    }
}
