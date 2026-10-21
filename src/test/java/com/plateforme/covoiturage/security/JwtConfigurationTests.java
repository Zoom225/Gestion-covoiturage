package com.plateforme.covoiturage.security;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class JwtConfigurationTests {
    @Test
    void applicationConfigurationHasNoFallbackSigningKey() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(JwtConfiguration.class)
                .withPropertyValues("driveconnect.security.jwt.issuer=driveconnect",
                        "driveconnect.security.jwt.audience=driveconnect-api",
                        "driveconnect.security.jwt.ttl=PT15M")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(
                            org.springframework.boot.context.properties.bind.validation.BindValidationException.class);
                });
    }

    @Test
    void refusesMissingMalformedAndShortSigningKeys() {
        var config = new JwtConfiguration();
        for (String key : new String[]{"", "not base64 !", "c2hvcnQ="}) {
            assertThatThrownBy(() -> config.jwtKey(new JwtProperties(key, "issuer", "audience", Duration.ofMinutes(15))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void refusesNonPositiveTtl() {
        for (Duration duration : new Duration[]{Duration.ZERO, Duration.ofSeconds(-1)}) {
            assertThatThrownBy(() -> new JwtProperties("unused", "issuer", "audience", duration))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
