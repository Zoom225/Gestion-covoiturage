package com.plateforme.covoiturage.dto.response;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(Instant timestamp, int status, String code, String message,
                               String path, List<ValidationError> validationErrors) {
    public record ValidationError(String field, String message) { }

    public static ApiErrorResponse of(int status, String code, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, code, message, path, List.of());
    }

    public static ApiErrorResponse unauthorized(String path) {
        return of(401, "UNAUTHORIZED", "Authentification requise ou invalide", path);
    }

    public static ApiErrorResponse forbidden(String path) {
        return of(403, "FORBIDDEN", "Accès interdit", path);
    }
}
