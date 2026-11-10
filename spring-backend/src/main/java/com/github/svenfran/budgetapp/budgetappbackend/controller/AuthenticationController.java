package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.config.ClientIpResolver;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationRequest;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationResponse;
import com.github.svenfran.budgetapp.budgetappbackend.dto.RegisterRequest;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.InvalidEmailException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.MissingDeviceIdException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.TooManyLoginAttemptsException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserAlreadyExistException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNameAlreadyExistsException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.service.AuthenticationService;
import com.github.svenfran.budgetapp.budgetappbackend.service.LoginService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    private final LoginService loginService;

    private final ClientIpResolver clientIpResolver;

    public AuthenticationController(AuthenticationService authenticationService, LoginService loginService,
                                    ClientIpResolver clientIpResolver) {
        this.authenticationService = authenticationService;
        this.loginService = loginService;
        this.clientIpResolver = clientIpResolver;
    }


    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(@Valid @RequestBody RegisterRequest request, BindingResult bindingResult) throws UserAlreadyExistException, InvalidEmailException, UserNotFoundException, UserNameAlreadyExistsException, MissingDeviceIdException {
        return ResponseEntity.ok(authenticationService.register(request, bindingResult));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(@Valid @RequestBody AuthenticationRequest request, BindingResult bindingResult,
                                                               HttpServletRequest httpRequest) throws UserNotFoundException, InvalidEmailException, MissingDeviceIdException, TooManyLoginAttemptsException {
        // Login-Rate-Limit (DIV-64): zählt den Versuch je Account/IP, 429 bei Überschreitung
        return ResponseEntity.ok(loginService.authenticate(request, bindingResult, clientIpResolver.resolve(httpRequest)));
    }

}
