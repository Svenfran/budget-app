package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.validator.ValidEmail;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class PasswordResetDto {

    @ValidEmail
    @NotNull
    @NotEmpty
    private String email;

    public PasswordResetDto(String email) {
        this.email = email;
    }

    public PasswordResetDto() {}

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
