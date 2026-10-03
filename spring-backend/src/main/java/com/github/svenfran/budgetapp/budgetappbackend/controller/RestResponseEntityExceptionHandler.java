package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;


@ControllerAdvice
public class RestResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    private final Logger LOG = LoggerFactory.getLogger(RestResponseEntityExceptionHandler.class);

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
            CategoryIsUsedByCartException.class
    })
    protected ResponseEntity<Object> handleNotFound(Exception ex, WebRequest request) {
        return buildResponse(ex, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(value = {
            AddCartCategoryNotFoundException.class,
            UpdateCartCategoryNotFoundException.class,
            UserAlreadyExistException.class,
            InvalidEmailException.class,
            UserNameAlreadyExistsException.class,
            UserIsNotAuthenticatedUser.class,
            WrongPasswordException.class,
            DatePurchasedNotWithinMembershipPeriodException.class,
            UserNameNotAllowedException.class
    })
    protected ResponseEntity<Object> handleBadRequest(Exception ex, WebRequest request) {
        return buildResponse(ex, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(value = {
            NotOwnerOfGroupException.class,
            MemberAlreadyExistsException.class,
            MemberEqualsOwnerException.class,
            NotMemberOfGroupException.class,
            NotOwnerOrMemberOfGroupException.class,
            NotOwnerOfCartException.class
    })
    protected ResponseEntity<Object> handleForbidden(Exception ex, WebRequest request) {
        return buildResponse(ex, HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<Object> buildResponse(Exception ex, HttpStatus status) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), status);
    }
}
