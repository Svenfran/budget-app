package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.dto.PasswordChangeDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.PasswordResetDto;
import com.github.svenfran.budgetapp.budgetappbackend.dto.UserDto;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.*;
import com.github.svenfran.budgetapp.budgetappbackend.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api")
public class UserProfileController {

    @Autowired
    private UserProfileService userProfileService;

    @DeleteMapping("/userprofile/delete/{userId}")
    public ResponseEntity<Void> deleteUserProfile(@PathVariable("userId") Long userId) throws UserNotFoundException, UserIsNotAuthenticatedUser {
        userProfileService.deleteUserProfile(userId);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PutMapping("/userprofile/update-username")
    public ResponseEntity<UserDto> changeUserName(@RequestBody UserDto userDto) throws UserNotFoundException, UserIsNotAuthenticatedUser, UserNameAlreadyExistsException {
        UserDto changedUser = userProfileService.changeUserName(userDto);
        return new ResponseEntity<>(changedUser, HttpStatus.CREATED);
    }

    @PutMapping("/userprofile/update-usermail")
    public ResponseEntity<UserDto> changeUserEmail(@Valid @RequestBody UserDto userDto, BindingResult bindingResult) throws UserNotFoundException, UserIsNotAuthenticatedUser, UserAlreadyExistException, InvalidEmailException {
        UserDto changedUser = userProfileService.changeUserEmail(userDto, bindingResult);
        return new ResponseEntity<>(changedUser, HttpStatus.CREATED);
    }

    @PutMapping("/userprofile/update-password")
    public ResponseEntity<Void> changePassword(@RequestBody PasswordChangeDto passwordChangeDto) throws UserNotFoundException, UserIsNotAuthenticatedUser, WrongPasswordException {
        userProfileService.changePassword(passwordChangeDto);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @PutMapping("/userprofile/password-reset")
    public ResponseEntity<Void> passwordReset(@Valid @RequestBody PasswordResetDto resetDto, BindingResult bindingResult) throws UserNotFoundException, InvalidEmailException {
        userProfileService.passwordReset(resetDto.getEmail(), bindingResult);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }
}
