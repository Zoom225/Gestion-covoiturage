package com.plateforme.covoiturage.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("driveconnect.security.jwt")
public record JwtProperties(@NotBlank String secret, @NotBlank String issuer,
                            @NotBlank String audience, @NotNull Duration ttl) {
    public JwtProperties {
        if (ttl != null && (ttl.isNegative() || ttl.isZero())) {
            throw new IllegalArgumentException("JWT TTL must be positive");
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=REDACTED]";
    }
}
