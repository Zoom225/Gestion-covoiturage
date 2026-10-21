package com.plateforme.covoiturage.dto.response;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    @Override
    public String toString() {
        return "TokenResponse[accessToken=REDACTED]";
    }
}
