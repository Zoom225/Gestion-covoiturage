package com.plateforme.covoiturage.exception;

import com.plateforme.covoiturage.dto.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.ValidationError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ApiErrorResponse(Instant.now(), 400,
                "VALIDATION_ERROR", "Données invalides", request.getRequestURI(), errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> malformedBody(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(400, "INVALID_REQUEST",
                "Requête JSON invalide", request.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiErrorResponse> authentication(HttpServletRequest request) {
        return ResponseEntity.status(401).header("WWW-Authenticate", "Bearer")
                .body(ApiErrorResponse.unauthorized(request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> forbidden(HttpServletRequest request) {
        return ResponseEntity.status(403).body(ApiErrorResponse.forbidden(request.getRequestURI()));
    }
}
