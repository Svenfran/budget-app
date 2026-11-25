package com.github.svenfran.budgetapp.budgetappbackend.dto;

/**
 * Antwort auf Login, Registrierung und Refresh. {@code token} ist der Access Token, {@code expirationDate} sein Ablauf
 * (ms); seit DIV-65 zusätzlich {@code refreshToken} und {@code sessionExpirationDate} (ms, absolute Höchstdauer der
 * Session). Ältere App-Versionen ignorieren die neuen Felder.
 */
public class AuthenticationResponse {

    private Long id;
    private String name;
    private String email;
    private Long expirationDate;
    private String token;
    private String refreshToken;
    private Long sessionExpirationDate;

    public AuthenticationResponse(Long id, String name, String email, Long expirationDate, String token,
                                  String refreshToken, Long sessionExpirationDate) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.expirationDate = expirationDate;
        this.token = token;
        this.refreshToken = refreshToken;
        this.sessionExpirationDate = sessionExpirationDate;
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

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Long getSessionExpirationDate() {
        return sessionExpirationDate;
    }

    public void setSessionExpirationDate(Long sessionExpirationDate) {
        this.sessionExpirationDate = sessionExpirationDate;
    }
}
