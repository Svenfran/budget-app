package com.github.svenfran.budgetapp.budgetappbackend.dto;

import com.github.svenfran.budgetapp.budgetappbackend.validator.ValidEmail;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class AuthenticationRequest {

    @ValidEmail
    @NotNull
    @NotEmpty
    private String email;
    private String password;
    private String deviceId;

    public AuthenticationRequest(String email, String password, String deviceId) {
        this.email = email;
        this.password = password;
        this.deviceId = deviceId;
    }

    public AuthenticationRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
}
