package com.plateforme.covoiturage;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	/** Les tests appellent le scheduler explicitement pour maîtriser l'horloge et les transactions. */
	@Bean
	org.springframework.test.context.DynamicPropertyRegistrar completionTestProperties() {
		return registry -> registry.add("driveconnect.scheduling.completion.enabled", () -> "false");
	}

	@Bean
	org.springframework.test.context.DynamicPropertyRegistrar jwtTestProperties() {
		byte[] key = new byte[64];
		new java.security.SecureRandom().nextBytes(key);
		String secret = java.util.Base64.getEncoder().encodeToString(key);
		return registry -> registry.add("driveconnect.security.jwt.secret", () -> secret);
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return disposablePostgres();
	}

	/** Retry container creation only, never a business assertion or a shared database. */
	static PostgreSQLContainer disposablePostgres() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16.6-alpine"))
				.withStartupAttempts(3);
	}

}
