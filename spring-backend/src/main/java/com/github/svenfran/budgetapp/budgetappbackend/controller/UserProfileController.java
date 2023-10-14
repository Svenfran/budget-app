package com.github.svenfran.budgetapp.budgetappbackend.controller;

import com.github.svenfran.budgetapp.budgetappbackend.exceptions.GroupNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.NotOwnerOfGroupException;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserIsNotAuthenticatedUser;
import com.github.svenfran.budgetapp.budgetappbackend.exceptions.UserNotFoundException;
import com.github.svenfran.budgetapp.budgetappbackend.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserProfileController {

    @Autowired
    private UserProfileService userProfileService;

    @DeleteMapping("/userprofile/delete/{userId}")
    public ResponseEntity<Void> deleteUserProfile(@PathVariable("userId") Long userId) throws UserNotFoundException, UserIsNotAuthenticatedUser, NotOwnerOfGroupException, GroupNotFoundException {
        userProfileService.deleteUserProfile(userId);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
