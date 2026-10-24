package com.plateforme.covoiturage.delivery;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("driveconnect.delivery")
public record DeliveryProperties(String keyId, String encryptionKey, String acceptanceUrl,
                                 String from, Duration retryDelay) {
    public DeliveryProperties {
        if (retryDelay != null && (retryDelay.isNegative() || retryDelay.isZero())) {
            throw new IllegalArgumentException("Delivery retry delay must be positive");
        }
    }
    @Override public String toString() { return "DeliveryProperties[secrets=REDACTED]"; }
}
