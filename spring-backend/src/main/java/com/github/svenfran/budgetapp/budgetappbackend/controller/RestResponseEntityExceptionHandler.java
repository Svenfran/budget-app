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

    @ExceptionHandler(value = { CartNotFoundException.class })
    protected ResponseEntity<Object> handleCartNotFoundException (CartNotFoundException ex, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(value = { AddCartCategoryNotFoundException.class })
    protected ResponseEntity<Object> handleAddCartCategoryNotFoundException (AddCartCategoryNotFoundException ex, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(value = { UpdateCartCategoryNotFoundException.class })
    protected ResponseEntity<Object> handleUpdateCartCategoryNotFoundException (UpdateCartCategoryNotFoundException ex, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(value = { CategoryNotFoundException.class })
    protected ResponseEntity<Object> handleCategoryNotFoundException (CategoryNotFoundException ex, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(value = { UserNotFoundException.class })
    protected ResponseEntity<Object> handleUserNotFoundException (UserNotFoundException ex, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(value = { GroupNotFoundException.class })
    protected ResponseEntity<Object> handleGroupNotFoundException (GroupNotFoundException ex, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(value = { NotOwnerOfGroupException.class })
    protected ResponseEntity<Object> handleNotOwnerOfGroupException (NotOwnerOfGroupException ex, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(value = { MemberAlreadyExixtsException.class })
    protected ResponseEntity<Object> handleMemberAlreadyExistsException (MemberAlreadyExixtsException ex, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(value = { MemberEqualsOwnerException.class })
    protected ResponseEntity<Object> handleMemberEqualsOwnerException (MemberEqualsOwnerException ex, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(value = { NotMemberOfGroupException.class })
    protected ResponseEntity<Object> handleNotMemberOfGroupException (NotMemberOfGroupException ex, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(value = { NotOwnerOrMemberOfGroupException.class })
    protected ResponseEntity<Object> handleNotOwnerOrMemberOfGroupException (NotOwnerOrMemberOfGroupException ex, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(value = { NotOwnerOfCartException.class })
    protected ResponseEntity<Object> handleNotOwnerOfCartException (NotOwnerOfCartException ex, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(value = { GroupIdNotFoundException.class })
    protected ResponseEntity<Object> handleGroupIdNotFoundException (GroupIdNotFoundException ex, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(value = { ShoppingListNotFoundException.class })
    protected ResponseEntity<Object> handleShoppingListNotFoundException (ShoppingListNotFoundException ex, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(value = { ShoppingItemNotFoundException.class })
    protected ResponseEntity<Object> handleShoppingItemNotFoundException (ShoppingItemNotFoundException ex, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(value = { ShoppingListDoesNotBelongToGroupException.class })
    protected ResponseEntity<Object> handleShoppingListDoesNotBelongToGroupException (ShoppingListDoesNotBelongToGroupException ex, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(value = { ShoppingItemDoesNotBelongToShoppingListException.class })
    protected ResponseEntity<Object> handleShoppingItemDoesNotBelongToShoppingListException (ShoppingItemDoesNotBelongToShoppingListException ex, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(value = { CategoryBelongsNotToGroupException.class })
    protected ResponseEntity<Object> handleSCategoryBelongsNotToGroupException (CategoryBelongsNotToGroupException ex, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(value = { CategoryIsUsedByCartException.class })
    protected ResponseEntity<Object> handleCategoryIsUsedByCartException (CategoryIsUsedByCartException ex, WebRequest request) {
        LOG.debug("Exception Message: " + ex.getMessage(), ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

}
