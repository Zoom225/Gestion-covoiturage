package com.plateforme.covoiturage.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("driveconnect.security.invitation")
public record InvitationProperties(Duration ttl) {
    public InvitationProperties {
        if (ttl != null && (ttl.isNegative() || ttl.isZero())) {
            throw new IllegalArgumentException("Invitation TTL must be positive");
        }
    }
}
