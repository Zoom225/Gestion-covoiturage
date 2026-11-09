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
                    "INTERNAL_ERROR", "Une erreur interne est survenue. Réessayez plus tard.", request.getRequestURI()));
        }
        return response.body(ApiErrorResponse.of(status, "HTTP_ERROR",
                "La requête ne peut pas être traitée avec cette méthode ou ce format.", request.getRequestURI()));
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
                "Le service de paiement est momentanément indisponible. Réessayez plus tard.",request.getRequestURI()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ApiErrorResponse> business(BusinessRuleException ex,HttpServletRequest request){
        String message=switch(ex.getCode()){
            case "INVALID_TIMEZONE" -> "Choisissez un fuseau horaire reconnu, par exemple Europe/Brussels.";
            case "UNAVAILABILITY_TARGET_REQUIRED" -> "Choisissez une seule cible pour l’indisponibilité : une zone ou un véhicule.";
            case "RESERVATION_NOT_CANCELLED" -> "Le remboursement concerne uniquement une réservation annulée.";
            case "ACTIVE_DEBT" -> "Une pénalité reste impayée. Réglez entièrement son solde dans « Paiements et dettes » avant de créer un trajet, réserver ou payer une place.";
            case "ALREADY_PAID" -> "Cette réservation est déjà payée. Retrouvez son paiement dans « Paiements et dettes ».";
            case "BOOKING_HORIZON" -> "Ce trajet n’est pas encore réservable pour votre catégorie. Les réservations ouvrent 30 jours avant le départ pour les VIP, 10 jours pour les clients locaux et 3 jours pour les occasionnels.";
            case "DEPARTURE_PASSED" -> "Le départ de ce trajet est atteint. Vous ne pouvez plus réserver, payer ou annuler une place.";
            case "DUPLICATE_RESERVATION" -> "Vous avez déjà une réservation active pour ce trajet.";
            case "IDEMPOTENCY_CONFLICT" -> "Cette demande de paiement concerne une autre réservation, une autre dette ou un autre montant. Consultez vos paiements avant de réessayer.";
            case "INVALID_DEBT_AMOUNT" -> "Le montant doit être positif, comporter au plus deux décimales et ne pas dépasser le solde restant.";
            case "INVALID_INTERVAL" -> "La date de fin doit être strictement postérieure à la date de début.";
            case "PENDING_RESERVATION_INCOMPATIBLE" -> "La modification rendrait le trajet inaccessible à un participant ayant réservé. Aucune modification n’a été effectuée.";
            case "PRIVATE_CANCELLATION_AVOIDS_J1_DEBT" -> "Cette annulation contournerait le contrôle J-1 et la dette prévus pour ce trajet privé.";
            case "RESOURCE_CHANGED" -> "La ressource a changé pendant l’opération. Actualisez les données puis réessayez.";
            case "RESOURCE_HAS_TRIP" -> "Cette indisponibilité ne peut pas être créée ou modifiée, car un trajet utilise déjà cette ressource sur la période choisie.";
            case "RESOURCE_UNAVAILABLE" -> "Ce véhicule ou cette zone n’est pas disponible pour la période sélectionnée.";
            case "TRIP_ARRIVAL_NOT_PASSED" -> "Le trajet ne peut pas encore être marqué comme réalisé avant son heure d’arrivée.";
            case "TRIP_ALREADY_STARTED" -> "Vous ne pouvez pas modifier ce trajet après son départ.";
            case "TRIP_NOT_MODIFIABLE" -> "Ce trajet n’est plus planifié et ne peut pas être modifié.";
            case "TRIP_HAS_CONFIRMED_RESERVATION" -> "Une réservation confirmée empêche la modification du trajet.";
            case "TRIP_HAS_PAYMENT" -> "Un paiement de place historique empêche la modification, même après annulation ou remboursement.";
            case "TRIP_J1_ALREADY_PROCESSED" -> "Le contrôle J-1 a déjà été effectué : le trajet ne peut plus être modifié.";
            case "TRIP_J1_VERIFICATION_REQUIRED" -> "L’historique de ce trajet doit être vérifié par un administrateur. Contactez-le avant de modifier le trajet.";
            case "TRIP_J1_DUE" -> "La vérification de la veille du départ est déjà due pour ce trajet privé. Son départ ne peut plus être modifié.";
            case "TRIP_HAS_DEBT" -> "Une dette liée au trajet empêche sa modification.";
            case "TRIP_LOCATIONS_LOCKED" -> "Les lieux de départ et d’arrivée ne peuvent pas être modifiés tant qu’une réservation est en attente. Les horaires et le véhicule restent soumis aux autres règles.";
            case "TRIP_FULL" -> "Les quatre places passagers de ce trajet sont déjà confirmées. Choisissez un autre trajet.";
            case "TRIP_NOT_OPEN" -> "Ce trajet n’est plus ouvert aux réservations, car il est annulé, complet ou terminé.";
            case "VEHICLE_CONFLICT" -> "Ce véhicule est déjà affecté à un trajet incompatible. Un délai de 15 minutes doit être respecté entre deux trajets.";
            case "VEHICLE_INACTIVE" -> "Ce véhicule est désactivé et ne peut pas être utilisé pour ce trajet.";
            case "VEHICLE_ZONE_MISMATCH" -> "Ce véhicule ne peut pas être utilisé dans une autre zone que celle du trajet.";
            case "VIP_REQUIRED" -> "Cette autorisation de zone ne peut être gérée que pour un client VIP.";
            case "ZONE_TIMEZONE_REQUIRED" -> "Cette zone n’a pas de fuseau horaire configuré. Contactez un administrateur avant de réserver.";
            case "UNAVAILABILITY_TARGET_IMMUTABLE" -> "La cible (zone ou véhicule) d’une indisponibilité ne peut pas être modifiée.";
            case "RESERVATION_NOT_PENDING" -> "Cette réservation n’est plus en attente et ne peut pas être réglée.";
            default -> "Opération incompatible avec les règles métier.";
        };
        int status = java.util.Set.of("INVALID_TIMEZONE", "UNAVAILABILITY_TARGET_REQUIRED")
                .contains(ex.getCode()) ? 400 : 409;
        return ResponseEntity.status(status).body(ApiErrorResponse.of(status,ex.getCode(),
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
                "Cette invitation n’est plus valide ou a déjà été utilisée. Demandez une nouvelle invitation à votre administrateur.",
                request.getRequestURI()));
    }

    @ExceptionHandler(InvitationConflictException.class)
    ResponseEntity<ApiErrorResponse> invitationConflict(HttpServletRequest request) {
        return ResponseEntity.status(409).body(ApiErrorResponse.of(409, "INVITATION_CONFLICT",
                "Cette invitation ne peut pas être créée ou renouvelée dans l’état actuel du compte.", request.getRequestURI()));
    }

    @ExceptionHandler(InvalidAccountRequestException.class)
    ResponseEntity<ApiErrorResponse> invalidAccount(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(400, "INVALID_REQUEST",
                "Les informations fournies sont invalides. Vérifiez les champs saisis.", request.getRequestURI()));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> integrity(HttpServletRequest request) {
        return ResponseEntity.status(409).body(ApiErrorResponse.of(409, "ACCOUNT_CONFLICT",
                "Cette opération entre en conflit avec des données existantes. Vérifiez les informations puis réessayez.",
                request.getRequestURI()));
    }

    @ExceptionHandler(DeliveryUnavailableException.class)
    ResponseEntity<ApiErrorResponse> deliveryUnavailable(HttpServletRequest request) {
        return ResponseEntity.status(503).body(ApiErrorResponse.of(503, "DELIVERY_UNAVAILABLE",
                "Le service d’envoi des invitations est momentanément indisponible. Réessayez plus tard.",
                request.getRequestURI()));
    }
}
