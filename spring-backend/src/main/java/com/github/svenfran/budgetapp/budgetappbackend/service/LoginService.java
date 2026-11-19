package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.config.ClientIp;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationRequest;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationResponse;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.InvalidEmailException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.MissingDeviceIdException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.TooManyLoginAttemptsException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

/**
 * Login mit Rate-Limit (DIV-64). Bewusst <b>nicht</b> transaktional: Der Versuch wird in einer eigenen Transaktion
 * gezählt ({@link LoginRateLimiter#acquire}), bevor {@link AuthenticationService#authenticate} in seiner Transaktion
 * das Passwort prüft – ein fehlgeschlagener Login rollt so nur seine eigene Transaktion zurück, der Zähler bleibt.
 *
 * <ul>
 *   <li>Ungültige E-Mail / fehlende DeviceId werden vorher abgewiesen und zählen nicht.</li>
 *   <li>Abgewiesen ({@link TooManyLoginAttemptsException}) → keine Passwortprüfung.</li>
 *   <li>Falsche Zugangsdaten → Versuch bleibt gezählt, Exception unverändert weiter (weiterhin 403, leerer Body).</li>
 *   <li>Erfolg → eigene Reservierung freigeben; ein technischer Fehler dabei bricht den bereits erfolgreichen Login
 *       nicht ab (der Zähler bleibt dann bis zum Fensterende konservativ zu hoch).</li>
 * </ul>
 */
@Service
public class LoginService {

    private static final Logger LOG = LoggerFactory.getLogger(LoginService.class);

    private final VerificationService verificationService;
    private final LoginRateLimiter loginRateLimiter;
    private final AuthenticationService authenticationService;

    public LoginService(VerificationService verificationService, LoginRateLimiter loginRateLimiter,
                        AuthenticationService authenticationService) {
        this.verificationService = verificationService;
        this.loginRateLimiter = loginRateLimiter;
        this.authenticationService = authenticationService;
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request, BindingResult bindingResult, ClientIp clientIp)
            throws InvalidEmailException, MissingDeviceIdException, TooManyLoginAttemptsException, UserNotFoundException {
        verificationService.verifyEmailIsValid(bindingResult, "email");
        verificationService.verifyDeviceIdIsPresent(bindingResult);

        var reservation = loginRateLimiter.acquire(request.getEmail(), clientIp);

        AuthenticationResponse response;
        try {
            response = authenticationService.authenticate(request, bindingResult);
        } catch (AuthenticationException e) {
            loginRateLimiter.logFailure(reservation);
            throw e;
        }

        try {
            loginRateLimiter.recordSuccess(reservation);
        } catch (RuntimeException e) {
            // Nur die Klasse loggen: keine Zugangsdaten, keine SQL-Details
            LOG.warn("event=login_rate_limit_release_failed account={} ip={} error={}",
                    reservation.accountPseudonym(), reservation.ipPseudonym(), e.getClass().getName());
        }
        return response;
    }
}
