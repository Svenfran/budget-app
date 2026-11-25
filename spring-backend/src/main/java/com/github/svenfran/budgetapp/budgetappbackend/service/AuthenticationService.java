package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.constants.SessionRevokeReason;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationRequest;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationResponse;
import com.github.svenfran.budgetapp.budgetappbackend.dto.RefreshRequest;
import com.github.svenfran.budgetapp.budgetappbackend.dto.RegisterRequest;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.InvalidEmailException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.InvalidRefreshTokenException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.MissingDeviceIdException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserAlreadyExistException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNameAlreadyExistsException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.helper.EmailUtils;
import com.github.svenfran.budgetapp.budgetappbackend.repository.TokenRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository;
import com.github.svenfran.budgetapp.budgetappbackend.service.auth.IssuedSession;
import com.github.svenfran.budgetapp.budgetappbackend.service.auth.RefreshResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;

import java.time.ZoneOffset;

/**
 * Registrierung, Login, Refresh und Logout (DIV-65: Access Token mit {@code sid} + Refresh-Token-Session statt
 * gespeichertem JWT). Sessions verwaltet {@link AuthSessionService}.
 */
@Service
public class AuthenticationService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    private final AuthenticationManager authManager;

    private final TokenRepository tokenRepository;

    private final VerificationService verificationService;

    private final GroupService groupService;

    private final AuthSessionService authSessionService;

    public AuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                                 AuthenticationManager authManager, TokenRepository tokenRepository,
                                 VerificationService verificationService, GroupService groupService,
                                 AuthSessionService authSessionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authManager = authManager;
        this.tokenRepository = tokenRepository;
        this.verificationService = verificationService;
        this.groupService = groupService;
        this.authSessionService = authSessionService;
    }

    @Transactional
    public AuthenticationResponse register(RegisterRequest request, BindingResult bindingResult) throws UserAlreadyExistException, InvalidEmailException, UserNotFoundException, UserNameAlreadyExistsException, MissingDeviceIdException {
        verificationService.verifyEmailIsValid(bindingResult, "email");
        verificationService.verifyDeviceIdIsPresent(bindingResult);
        verificationService.verifyEmailNotExists(request.getEmail());
        // Umgebende Leerzeichen nicht mitspeichern (das Frontend trimmt auch, der Server verlässt sich nicht darauf)
        var userName = request.getName() == null ? null : request.getName().trim();
        verificationService.verifyUserNameNotExists(userName);
        var user = new User();
        user.setName(userName);
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        var savedUser = userRepository.save(user);
        groupService.createDefaultGroup(savedUser);
        return response(authSessionService.createSession(savedUser, request.getDeviceId()));
    }

    /**
     * Login unter Nutzersperre (DIV-65): Zuerst wird die Nutzerzeile per normalisierter E-Mail gesperrt, danach das
     * Passwort geprüft und die Session angelegt. Eine parallele Passwortänderung ist so entweder vorher committet
     * (alte Zugangsdaten scheitern) oder wartet und widerruft danach auch die neue Session. Bei unbekannter E-Mail
     * wird die Prüfung trotzdem ausgeführt (Timing-Schutz des {@code DaoAuthenticationProvider}) und scheitert.
     */
    @Transactional
    public AuthenticationResponse authenticate(AuthenticationRequest request, BindingResult bindingResult) throws UserNotFoundException, InvalidEmailException, MissingDeviceIdException {
        verificationService.verifyEmailIsValid(bindingResult, "email");
        verificationService.verifyDeviceIdIsPresent(bindingResult);
        // erster Zugriff auf die Nutzer-Entity in dieser Transaktion: der UserDetailsService erhält danach dieselbe,
        // unter Sperre geladene Instanz
        var lockedUser = userRepository.findByEmailForUpdate(EmailUtils.normalize(request.getEmail()));
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        // nur möglich, wenn der Nutzer zwischen Sperre und Prüfung angelegt wurde: keine Session ohne Sperre
        var user = lockedUser.orElseThrow(() -> new BadCredentialsException("Bad credentials"));
        return response(authSessionService.createSession(user, request.getDeviceId()));
    }

    /**
     * Rotiert den Refresh Token (DIV-65). Bewusst <b>nicht</b> transaktional: {@link AuthSessionService#refresh}
     * committet seine Transaktion (auch einen Replay-Widerruf), erst danach wird eine Ablehnung zur Exception (401).
     */
    public AuthenticationResponse refresh(RefreshRequest request, BindingResult bindingResult)
            throws MissingDeviceIdException, InvalidRefreshTokenException {
        verificationService.verifyDeviceIdIsPresent(bindingResult);
        var result = authSessionService.refresh(request.getRefreshToken(), request.getDeviceId());
        if (result instanceof RefreshResult.Rotated rotated) {
            return response(rotated.session());
        }
        throw new InvalidRefreshTokenException();
    }

    /**
     * Logout (DIV-65): widerruft die Session des Refresh Tokens und/oder die Session aus dem {@code sid} des
     * Bearer-Tokens – auch eines abgelaufenen, solange die Signatur gültig ist. Unbekannte oder fehlende Tokens werden
     * ignoriert (Antwort immer 200, kein Orakel). Nicht transaktional: jeder Widerruf committet einzeln.
     */
    public void logout(String refreshToken, String authorizationHeader) {
        authSessionService.revokeByRefreshToken(refreshToken, SessionRevokeReason.LOGOUT);
        if (authorizationHeader != null && authorizationHeader.startsWith(JwtService.BEARER_PREFIX)) {
            jwtService.extractSessionIdIgnoringExpiry(authorizationHeader.substring(JwtService.BEARER_PREFIX.length()))
                    .ifPresent(sessionId -> authSessionService.revokeBySessionId(sessionId, SessionRevokeReason.LOGOUT));
        }
    }

    private static AuthenticationResponse response(IssuedSession issued) {
        return new AuthenticationResponse(issued.userId(), issued.userName(), issued.userEmail(),
                issued.accessTokenExpiresAt().toEpochMilli(), issued.accessToken(), issued.refreshToken(),
                issued.sessionExpiresAt().toInstant(ZoneOffset.UTC).toEpochMilli());
    }

    @Scheduled(cron = "${task.clearToken.schedule}")
    public void deleteExpiredTokens() {
        var tokens = tokenRepository.findAllByExpiredIsTrueAndRevokedIsTrue();
        tokenRepository.deleteAll(tokens);
    }

}
