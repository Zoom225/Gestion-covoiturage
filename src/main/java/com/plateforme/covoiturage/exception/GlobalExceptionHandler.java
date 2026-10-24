package com.plateforme.covoiturage.exception;

import com.plateforme.covoiturage.dto.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BindException.class)
    ResponseEntity<ApiErrorResponse> validation(BindException ex, HttpServletRequest request) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.ValidationError(error.getField(), error.isBindingFailure() ? "Valeur invalide" : error.getDefaultMessage()))
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
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> invalidParameter(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(400, "INVALID_REQUEST",
                "Paramètre invalide", request.getRequestURI()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiErrorResponse> methodValidation(HandlerMethodValidationException ex,
                                                     HttpServletRequest request) {
        int status = ex.getStatusCode().value();
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status,
                status == 400 ? "VALIDATION_ERROR" : "INTERNAL_ERROR",
                status == 400 ? "Données invalides" : "Erreur interne", request.getRequestURI()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(HttpServletRequest request) {
        return ResponseEntity.status(404).body(ApiErrorResponse.of(404, "NOT_FOUND",
                "Ressource introuvable", request.getRequestURI()));
    }

    @ExceptionHandler(InvitationInvalideException.class)
    ResponseEntity<ApiErrorResponse> invalidInvitation(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(400, "INVALID_INVITATION",
                "Invitation indisponible ou invalide", request.getRequestURI()));
    }

    @ExceptionHandler(InvitationConflictException.class)
    ResponseEntity<ApiErrorResponse> invitationConflict(HttpServletRequest request) {
        return ResponseEntity.status(409).body(ApiErrorResponse.of(409, "INVITATION_CONFLICT",
                "Opération d'invitation incompatible avec l'état courant", request.getRequestURI()));
    }

    @ExceptionHandler(InvalidAccountRequestException.class)
    ResponseEntity<ApiErrorResponse> invalidAccount(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(400, "INVALID_REQUEST",
                "Données de compte invalides", request.getRequestURI()));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> integrity(HttpServletRequest request) {
        return ResponseEntity.status(409).body(ApiErrorResponse.of(409, "ACCOUNT_CONFLICT",
                "Opération incompatible avec les données existantes", request.getRequestURI()));
    }

    @ExceptionHandler(DeliveryUnavailableException.class)
    ResponseEntity<ApiErrorResponse> deliveryUnavailable(HttpServletRequest request) {
        return ResponseEntity.status(503).body(ApiErrorResponse.of(503, "DELIVERY_UNAVAILABLE",
                "Livraison d'invitation indisponible", request.getRequestURI()));
    }
}
