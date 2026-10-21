package com.plateforme.covoiturage.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;

import java.io.IOException;

// Conserve les vérifications CORS de Spring et réutilise le format d'erreur API.
final class ApiCorsProcessor extends DefaultCorsProcessor {
    private final ApiSecurityErrorHandler errors;

    ApiCorsProcessor(ApiSecurityErrorHandler errors) {
        this.errors = errors;
    }

    @Override
    public boolean processRequest(CorsConfiguration config, HttpServletRequest request,
                                  HttpServletResponse response) throws IOException {
        boolean accepted = super.processRequest(config, request, response);
        if (!accepted) {
            errors.handle(request, response, new AccessDeniedException("Origine CORS interdite"));
        }
        return accepted;
    }

    @Override
    protected void rejectRequest(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
    }
}
