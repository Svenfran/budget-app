package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationRequest;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationResponse;
import com.github.svenfran.budgetapp.budgetappbackend.dto.RegisterRequest;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.InvalidEmailException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserAlreadyExistException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNameAlreadyExistsException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.service.AuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    @Autowired
    private AuthenticationService authenticationService;


    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(@Valid @RequestBody RegisterRequest request, BindingResult bindingResult) throws UserAlreadyExistException, InvalidEmailException, UserNotFoundException, UserNameAlreadyExistsException {
        return ResponseEntity.ok(authenticationService.register(request, bindingResult));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(@Valid @RequestBody AuthenticationRequest request, BindingResult bindingResult) throws UserNotFoundException, InvalidEmailException {
        return ResponseEntity.ok(authenticationService.authenticate(request, bindingResult));
    }

}
