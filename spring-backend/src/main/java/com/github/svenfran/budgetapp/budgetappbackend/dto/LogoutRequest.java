package com.github.svenfran.budgetapp.budgetappbackend.dto;

/**
 * Optionaler Body von {@code POST /api/auth/logout} (DIV-65): der Refresh Token der zu beendenden Session.
 * {@link #toString()} enthält den Token nicht.
 */
public class LogoutRequest {

    private String refreshToken;

    public LogoutRequest() {
    }

    public LogoutRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    @Override
    public String toString() {
        return "LogoutRequest{}";
    }
}
