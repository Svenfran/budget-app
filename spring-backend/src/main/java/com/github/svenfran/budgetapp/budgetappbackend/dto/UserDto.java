package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.entity.User;
import com.github.svenfran.budgetapp.budgetappbackend.validator.ValidEmail;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class UserDto {

    private Long id;
    private String userName;
    @ValidEmail
    @NotNull
    @NotEmpty
    private String userEmail;

    public UserDto(User user) {
        this.id = user.getId();
        this.userName = user.getName();
    }

    public UserDto(User user, String userEmail) {
        this.id = user.getId();
        this.userName = user.getName();
        this.userEmail = userEmail;
    }

    public UserDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }
}
