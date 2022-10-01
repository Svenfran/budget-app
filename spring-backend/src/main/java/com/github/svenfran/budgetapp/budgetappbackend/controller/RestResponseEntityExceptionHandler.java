package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.Exceptions.*;
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


}
