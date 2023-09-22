package com.github.svenfran.budgetapp.budgetappbackend.service;

import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationRequest;
import com.github.svenfran.budgetapp.budgetappbackend.dto.AuthenticationResponse;
import com.github.svenfran.budgetapp.budgetappbackend.dto.RegisterRequest;
import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.InvalidEmailException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserAlreadyExistException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNameAlreadyExistsException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
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

    public AuthenticationResponse register(RegisterRequest request, BindingResult bindingResult) throws UserAlreadyExistException, InvalidEmailException, UserNotFoundException, UserNameAlreadyExistsException {
        verifyEmailIsValid(bindingResult);
        verifyEmailNotExists(request.getEmail());
        verifyUserNameExists(request.getName());
        var user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
        var registeredUserId = dataLoaderService.loadUserByEmail(request.getEmail()).getId();
        var token = jwtService.generateToken(user);
        var expTime = jwtService.extractExpiration(token).getTime();
        return new AuthenticationResponse(registeredUserId, request.getName(), expTime, token);
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
        return new AuthenticationResponse(user.getId(), user.getName(), expTime, token);
    }

    private boolean emailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    private boolean userNameExists(String userNAme) {
        return userRepository.findByName(userNAme).isPresent();
    }

    private void verifyEmailNotExists(String email) throws UserAlreadyExistException {
        if (emailExists(email)) {
            throw new UserAlreadyExistException(String.format("User with email %s already exists", email));
        }
    }

    private void verifyEmailIsValid(BindingResult bindingResult) throws InvalidEmailException {
        if (bindingResult.hasErrors()) {
            throw new InvalidEmailException("Invalid Email");
        }
    }

    private void verifyUserNameExists(String userName) throws UserNameAlreadyExistsException {
        if (userNameExists(userName)) {
            throw new UserNameAlreadyExistsException(String.format("User with name %s already exists", userName));
        }
    }
}
