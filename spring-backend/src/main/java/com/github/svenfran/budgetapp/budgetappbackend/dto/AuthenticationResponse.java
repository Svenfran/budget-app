package com.github.svenfran.budgetapp.budgetappbackend.dto;

public class AuthenticationResponse {

    private Long id;
    private String name;
    private String email;
    private Long expirationDate;
    private String token;

    public AuthenticationResponse(Long id, String name, String email, Long expirationDate, String token) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.expirationDate = expirationDate;
        this.token = token;
    }

    public AuthenticationResponse() {
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(Long expirationDate) {
        this.expirationDate = expirationDate;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
