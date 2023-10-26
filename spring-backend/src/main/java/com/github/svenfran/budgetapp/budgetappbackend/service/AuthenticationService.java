package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.constants.TokenType;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationRequest;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationResponse;
import com.github.svenfran.budgetapp.budgetappbackend.dto.RegisterRequest;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Token;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.InvalidEmailException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserAlreadyExistException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNameAlreadyExistsException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.repository.TokenRepository;
import com.github.svenfran.budgetapp.budgetappbackend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

@Service
public class AuthenticationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataLoaderService dataLoaderService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthenticationManager authManager;

    @Autowired
    private TokenRepository tokenRepository;


    public AuthenticationResponse register(RegisterRequest request, BindingResult bindingResult) throws UserAlreadyExistException, InvalidEmailException, UserNotFoundException, UserNameAlreadyExistsException {
        verifyEmailIsValid(bindingResult);
        verifyEmailNotExists(request.getEmail());
        verifyUserNameNotExists(request.getName());
        var user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        var savedUser = userRepository.save(user);
        var registeredUserId = dataLoaderService.loadUserByEmail(request.getEmail()).getId();
        var token = jwtService.generateToken(user);
        var expTime = jwtService.extractExpiration(token).getTime();
        saveUserToken(savedUser, token);
        return new AuthenticationResponse(registeredUserId, request.getName(), request.getEmail(), expTime, token);
    }


    public AuthenticationResponse authenticate(AuthenticationRequest request, BindingResult bindingResult) throws UserNotFoundException, InvalidEmailException {
        verifyEmailIsValid(bindingResult);
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        var user = dataLoaderService.loadUserByEmail(request.getEmail());
        var token = jwtService.generateToken(user);
        var expTime = jwtService.extractExpiration(token).getTime();
        revokeAllUserTokens(user);
        saveUserToken(user, token);
        return new AuthenticationResponse(user.getId(), user.getName(), user.getEmail(), expTime, token);
    }

    private boolean emailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    private boolean userNameExists(String userNAme) {
        return userRepository.findByName(userNAme).isPresent();
    }

    public void verifyEmailNotExists(String email) throws UserAlreadyExistException {
        if (emailExists(email)) {
            throw new UserAlreadyExistException(String.format("User with email %s already exists", email));
        }
    }

    public void verifyEmailIsValid(BindingResult bindingResult) throws InvalidEmailException {
        if (bindingResult.hasErrors()) {
            throw new InvalidEmailException("Invalid Email");
        }
    }

    public void verifyUserNameNotExists(String userName) throws UserNameAlreadyExistsException {
        if (userNameExists(userName)) {
            throw new UserNameAlreadyExistsException(String.format("User with name %s already exists", userName));
        }
    }

    private void revokeAllUserTokens(User user) {
        var validUserTokens = tokenRepository.findAllValidTokensByUser(user.getId());
        if (validUserTokens.isEmpty()) {
            return;
        }
        validUserTokens.forEach(token -> {
            token.setExpired(true);
            token.setRevoked(true);
        });
        tokenRepository.saveAll(validUserTokens);
    }

    private void saveUserToken(User user, String token) {
        var userToken = new Token();
        userToken.setUser(user);
        userToken.setToken(token);
        userToken.setTokenType(TokenType.BEARER);
        userToken.setExpired(false);
        userToken.setRevoked(false);
        tokenRepository.save(userToken);
    }
}
