package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.constants.TokenType;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationRequest;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationResponse;
import com.github.svenfran.budgetapp.budgetappbackend.dto.RegisterRequest;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Token;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.InvalidEmailException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.MissingDeviceIdException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserAlreadyExistException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNameAlreadyExistsException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.repository.TokenRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;

    private final DataLoaderService dataLoaderService;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    private final AuthenticationManager authManager;

    private final TokenRepository tokenRepository;

    private final VerificationService verificationService;

    private final GroupService groupService;

    public AuthenticationService(UserRepository userRepository, DataLoaderService dataLoaderService,
                                 PasswordEncoder passwordEncoder, JwtService jwtService,
                                 AuthenticationManager authManager, TokenRepository tokenRepository,
                                 VerificationService verificationService, GroupService groupService) {
        this.userRepository = userRepository;
        this.dataLoaderService = dataLoaderService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authManager = authManager;
        this.tokenRepository = tokenRepository;
        this.verificationService = verificationService;
        this.groupService = groupService;
    }

    @Transactional
    public AuthenticationResponse register(RegisterRequest request, BindingResult bindingResult) throws UserAlreadyExistException, InvalidEmailException, UserNotFoundException, UserNameAlreadyExistsException, MissingDeviceIdException {
        verificationService.verifyEmailIsValid(bindingResult, "email");
        verificationService.verifyDeviceIdIsPresent(bindingResult);
        verificationService.verifyEmailNotExists(request.getEmail());
        verificationService.verifyUserNameNotExists(request.getName());
        var user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        var savedUser = userRepository.save(user);
        var token = jwtService.generateToken(user, request.getDeviceId());
        var expTime = jwtService.extractExpiration(token).getTime();
        saveUserToken(savedUser, token, request.getDeviceId());
        groupService.createDefaultGroup(savedUser);
        return new AuthenticationResponse(savedUser.getId(), request.getName(), savedUser.getEmail(), expTime, token);
    }

    @Transactional
    public AuthenticationResponse authenticate(AuthenticationRequest request, BindingResult bindingResult) throws UserNotFoundException, InvalidEmailException, MissingDeviceIdException {
        verificationService.verifyEmailIsValid(bindingResult, "email");
        verificationService.verifyDeviceIdIsPresent(bindingResult);
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        var user = dataLoaderService.loadUserByEmail(request.getEmail());
        var token = jwtService.generateToken(user, request.getDeviceId());
        var expTime = jwtService.extractExpiration(token).getTime();
        revokeAllUserTokens(user, request.getDeviceId());
        deleteInvalidUserTokens(user, request.getDeviceId());
        saveUserToken(user, token, request.getDeviceId());
        return new AuthenticationResponse(user.getId(), user.getName(), user.getEmail(), expTime, token);
    }

    private void revokeAllUserTokens(User user, String deviceId) {
        var validUserTokens = tokenRepository.findAllValidTokensByUserAndDeviceId(user.getId(), deviceId);
        if (validUserTokens.isEmpty()) {
            return;
        }
        validUserTokens.forEach(token -> {
            token.setExpired(true);
            token.setRevoked(true);
        });
        tokenRepository.saveAll(validUserTokens);
    }

    private void deleteInvalidUserTokens(User user, String deviceId) {
        var invalidTokens = tokenRepository.findAllInvalidTokensByUserAndDeviceId(user.getId(), deviceId);
        tokenRepository.deleteAll(invalidTokens);
    }

    private void saveUserToken(User user, String token, String deviceId) {
        var userToken = new Token();
        userToken.setUser(user);
        userToken.setToken(token);
        userToken.setTokenType(TokenType.BEARER);
        userToken.setExpired(false);
        userToken.setRevoked(false);
        userToken.setDeviceId(deviceId);
        tokenRepository.save(userToken);
    }

    @Scheduled(cron = "${task.clearToken.schedule}")
    public void deleteExpiredTokens() {
        var tokens = tokenRepository.findAllByExpiredIsTrueAndRevokedIsTrue();
        tokenRepository.deleteAll(tokens);
    }

}
