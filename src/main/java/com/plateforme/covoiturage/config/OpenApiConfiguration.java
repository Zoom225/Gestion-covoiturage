package com.plateforme.covoiturage.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    public static final String BEARER_JWT = "bearerJwt";

    @Bean
    OpenAPI driveConnectOpenApi(@Value("${driveconnect.api.version}") String version) {
        return new OpenAPI()
                .info(new Info().title("DriveConnect — API REST")
                        .description("Plateforme de gestion d'un réseau de covoiturage multi-zones")
                        .version(version))
                .components(new Components().addSecuritySchemes(BEARER_JWT,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("Saisir le JWT obtenu à la connexion, sans le préfixe Bearer.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_JWT));
    }
}
