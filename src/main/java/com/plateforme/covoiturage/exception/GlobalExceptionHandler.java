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
@lombok.extern.slf4j.Slf4j
public class GlobalExceptionHandler {
    /** Le détail d'une exception/cause ne constitue jamais un message public ou une trace sûre. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception failure, HttpServletRequest request) {
        var protocol = failure instanceof org.springframework.web.ErrorResponse error ? error : null;
        int status = protocol == null ? 500 : protocol.getStatusCode().value();
        var response = ResponseEntity.status(status).cacheControl(org.springframework.http.CacheControl.noStore());
        if (protocol != null) {
            // Conserver les indications HTTP nécessaires sans recopier des en-têtes sensibles.
            for (String name : java.util.List.of("Allow", "Accept", "Accept-Patch", "Retry-After")) {
                var values = protocol.getHeaders().get(name);
                if (values != null) response.header(name, values.toArray(String[]::new));
            }
        }
        if (status >= 500) {
            String reference = java.util.UUID.randomUUID().toString();
            log.error("erreur_interne reference={} type={}", reference, failure.getClass().getName());
            return response.header("X-Error-Id", reference).body(ApiErrorResponse.of(status,
                    "INTERNAL_ERROR", "Erreur interne", request.getRequestURI()));
        }
        return response.body(ApiErrorResponse.of(status, "HTTP_ERROR",
                "Requête incompatible avec le protocole HTTP", request.getRequestURI()));
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<ApiErrorResponse> validation(BindException ex, HttpServletRequest request) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.ValidationError(error.getField(), error.isBindingFailure() ? "Valeur invalide" : error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ApiErrorResponse(Instant.now(), 400,
                "VALIDATION_ERROR", "Données invalides", request.getRequestURI(), errors));
    }

    @ExceptionHandler(org.springframework.web.bind.ServletRequestBindingException.class)
    ResponseEntity<ApiErrorResponse> missingParameter(HttpServletRequest request){
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(400,"INVALID_REQUEST",
                "Paramètre requis absent ou invalide.",request.getRequestURI()));
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

    @ExceptionHandler(PaymentUnavailableException.class)
    ResponseEntity<ApiErrorResponse> paymentUnavailable(HttpServletRequest request){
        return ResponseEntity.status(503).body(ApiErrorResponse.of(503,"PAYMENT_UNAVAILABLE",
                "Validation de paiement indisponible",request.getRequestURI()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ApiErrorResponse> business(BusinessRuleException ex,HttpServletRequest request){
        String message=switch(ex.getCode()){
            case "TRIP_ALREADY_STARTED" -> "Un trajet déjà parti ne peut pas être modifié.";
            case "TRIP_NOT_MODIFIABLE" -> "Seul un trajet PLANIFIE peut être modifié.";
            case "TRIP_HAS_CONFIRMED_RESERVATION" -> "Une réservation confirmée empêche la modification du trajet.";
            case "TRIP_HAS_PAYMENT" -> "Un paiement de place historique empêche la modification, même après annulation ou remboursement.";
            case "TRIP_J1_ALREADY_PROCESSED" -> "Le contrôle J-1 a déjà été effectué : le trajet ne peut plus être modifié.";
            case "TRIP_J1_VERIFICATION_REQUIRED" -> "Une vérification de l'historique J-1 est nécessaire avant toute modification.";
            case "TRIP_J1_DUE" -> "La modification ne peut pas déplacer un contrôle J-1 déjà dû.";
            case "TRIP_HAS_DEBT" -> "Une dette liée au trajet empêche sa modification.";
            case "PENDING_RESERVATION_INCOMPATIBLE" -> "Les nouveaux horaires sont incompatibles avec une réservation EN_ATTENTE ; aucune réservation n'a été modifiée.";
            case "TRIP_LOCATIONS_LOCKED" -> "Les lieux ne peuvent pas être modifiés tant qu'une réservation EN_ATTENTE est active, organisateur inclus. Les horaires et le véhicule restent modifiables sous les autres conditions.";
            default -> "Opération incompatible avec les règles métier.";
        };
        return ResponseEntity.status(409).body(ApiErrorResponse.of(409,ex.getCode(),
                message,request.getRequestURI()));
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
