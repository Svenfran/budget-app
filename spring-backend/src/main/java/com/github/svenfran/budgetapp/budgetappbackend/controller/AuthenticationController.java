package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.config.ClientIpResolver;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationRequest;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationResponse;
import com.github.svenfran.budgetapp.budgetappbackend.dto.LogoutRequest;
import com.github.svenfran.budgetapp.budgetappbackend.dto.RefreshRequest;
import com.github.svenfran.budgetapp.budgetappbackend.dto.RegisterRequest;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.InvalidEmailException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.InvalidRefreshTokenException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.MissingDeviceIdException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.TooManyLoginAttemptsException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserAlreadyExistException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNameAlreadyExistsException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.service.AuthenticationService;
import com.github.svenfran.budgetapp.budgetappbackend.service.LoginService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    /** Neues Token-Paar gegen einen gültigen Refresh Token (DIV-65); jede Ablehnung ist 401 REFRESH_TOKEN_INVALID. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponse> refresh(@Valid @RequestBody RefreshRequest request, BindingResult bindingResult)
            throws MissingDeviceIdException, InvalidRefreshTokenException {
        return ResponseEntity.ok(authenticationService.refresh(request, bindingResult));
    }

    /**
     * Beendet die Session des Refresh Tokens im Body und/oder die des (auch abgelaufenen) Bearer-Tokens (DIV-65).
     * Immer 200, auch ohne oder mit unbekanntem Token.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request,
                                       @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authenticationService.logout(request == null ? null : request.getRefreshToken(), authorization);
        return ResponseEntity.ok().build();
    }

}
