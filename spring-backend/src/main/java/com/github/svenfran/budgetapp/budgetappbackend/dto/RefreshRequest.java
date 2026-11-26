package com.github.svenfran.budgetapp.budgetappbackend.dto;

import javax.validation.constraints.NotBlank;

/**
 * Body von {@code POST /api/auth/refresh} (DIV-65). Die DeviceId steht wie bei Login/Registrierung im Body; fehlt
 * sie, antwortet der Endpunkt mit 400 {@code DEVICE_ID_REQUIRED}. Ein fehlender Refresh Token ist ein ungültiger
 * Token (401). {@link #toString()} enthält den Token nicht.
 */
public class RefreshRequest {

    private String refreshToken;
    @NotBlank
    private String deviceId;

    public RefreshRequest() {
    }

    public RefreshRequest(String refreshToken, String deviceId) {
        this.refreshToken = refreshToken;
        this.deviceId = deviceId;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public String toString() {
        return "RefreshRequest{deviceId='" + deviceId + "'}";
    }
}
