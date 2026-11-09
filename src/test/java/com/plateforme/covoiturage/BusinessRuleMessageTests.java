package com.plateforme.covoiturage;

import com.plateforme.covoiturage.dto.response.ApiErrorResponse;
import com.plateforme.covoiturage.exception.BusinessRuleException;
import com.plateforme.covoiturage.exception.GlobalExceptionHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BusinessRuleMessageTests {
    @RestController
    static class Probe {
        @GetMapping("/test-only/business/{code}")
        void business(@PathVariable String code) {
            throw new BusinessRuleException(code);
        }

        @GetMapping("/test-only/unauthorized")
        void unauthorized() {
            throw new BadCredentialsException("detail technique privé");
        }

        @GetMapping("/test-only/forbidden")
        void forbidden() {
            throw new AccessDeniedException("detail technique privé");
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Probe())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    static Stream<Arguments> businessMessages() {
        return Stream.of(
                Arguments.of("ACTIVE_DEBT", "Une pénalité reste impayée. Réglez entièrement son solde dans « Paiements et dettes » avant de créer un trajet, réserver ou payer une place."),
                Arguments.of("ALREADY_PAID", "Cette réservation est déjà payée. Retrouvez son paiement dans « Paiements et dettes »."),
                Arguments.of("BOOKING_HORIZON", "Ce trajet n’est pas encore réservable pour votre catégorie. Les réservations ouvrent 30 jours avant le départ pour les VIP, 10 jours pour les clients locaux et 3 jours pour les occasionnels."),
                Arguments.of("DEPARTURE_PASSED", "Le départ de ce trajet est atteint. Vous ne pouvez plus réserver, payer ou annuler une place."),
                Arguments.of("DUPLICATE_RESERVATION", "Vous avez déjà une réservation active pour ce trajet."),
                Arguments.of("IDEMPOTENCY_CONFLICT", "Cette demande de paiement concerne une autre réservation, une autre dette ou un autre montant. Consultez vos paiements avant de réessayer."),
                Arguments.of("INVALID_DEBT_AMOUNT", "Le montant doit être positif, comporter au plus deux décimales et ne pas dépasser le solde restant."),
                Arguments.of("INVALID_INTERVAL", "La date de fin doit être strictement postérieure à la date de début."),
                Arguments.of("PENDING_RESERVATION_INCOMPATIBLE", "La modification rendrait le trajet inaccessible à un participant ayant réservé. Aucune modification n’a été effectuée."),
                Arguments.of("PRIVATE_CANCELLATION_AVOIDS_J1_DEBT", "Cette annulation contournerait le contrôle J-1 et la dette prévus pour ce trajet privé."),
                Arguments.of("RESOURCE_CHANGED", "La ressource a changé pendant l’opération. Actualisez les données puis réessayez."),
                Arguments.of("RESOURCE_HAS_TRIP", "Cette indisponibilité ne peut pas être créée ou modifiée, car un trajet utilise déjà cette ressource sur la période choisie."),
                Arguments.of("RESOURCE_UNAVAILABLE", "Ce véhicule ou cette zone n’est pas disponible pour la période sélectionnée."),
                Arguments.of("TRIP_ARRIVAL_NOT_PASSED", "Le trajet ne peut pas encore être marqué comme réalisé avant son heure d’arrivée."),
                Arguments.of("TRIP_ALREADY_STARTED", "Vous ne pouvez pas modifier ce trajet après son départ."),
                Arguments.of("TRIP_NOT_MODIFIABLE", "Ce trajet n’est plus planifié et ne peut pas être modifié."),
                Arguments.of("TRIP_HAS_CONFIRMED_RESERVATION", "Une réservation confirmée empêche la modification du trajet."),
                Arguments.of("TRIP_HAS_PAYMENT", "Un paiement de place historique empêche la modification, même après annulation ou remboursement."),
                Arguments.of("TRIP_J1_ALREADY_PROCESSED", "Le contrôle J-1 a déjà été effectué : le trajet ne peut plus être modifié."),
                Arguments.of("TRIP_J1_VERIFICATION_REQUIRED", "L’historique de ce trajet doit être vérifié par un administrateur. Contactez-le avant de modifier le trajet."),
                Arguments.of("TRIP_J1_DUE", "La vérification de la veille du départ est déjà due pour ce trajet privé. Son départ ne peut plus être modifié."),
                Arguments.of("TRIP_HAS_DEBT", "Une dette liée au trajet empêche sa modification."),
                Arguments.of("TRIP_LOCATIONS_LOCKED", "Les lieux de départ et d’arrivée ne peuvent pas être modifiés tant qu’une réservation est en attente. Les horaires et le véhicule restent soumis aux autres règles."),
                Arguments.of("TRIP_FULL", "Les quatre places passagers de ce trajet sont déjà confirmées. Choisissez un autre trajet."),
                Arguments.of("TRIP_NOT_OPEN", "Ce trajet n’est plus ouvert aux réservations, car il est annulé, complet ou terminé."),
                Arguments.of("VEHICLE_CONFLICT", "Ce véhicule est déjà affecté à un trajet incompatible. Un délai de 15 minutes doit être respecté entre deux trajets."),
                Arguments.of("VEHICLE_INACTIVE", "Ce véhicule est désactivé et ne peut pas être utilisé pour ce trajet."),
                Arguments.of("VEHICLE_ZONE_MISMATCH", "Ce véhicule ne peut pas être utilisé dans une autre zone que celle du trajet."),
                Arguments.of("VIP_REQUIRED", "Cette autorisation de zone ne peut être gérée que pour un client VIP."),
                Arguments.of("ZONE_TIMEZONE_REQUIRED", "Cette zone n’a pas de fuseau horaire configuré. Contactez un administrateur avant de réserver."),
                Arguments.of("UNAVAILABILITY_TARGET_IMMUTABLE", "La cible (zone ou véhicule) d’une indisponibilité ne peut pas être modifiée."),
                Arguments.of("RESERVATION_NOT_PENDING", "Cette réservation n’est plus en attente et ne peut pas être réglée.")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidReferenceMessages")
    void referenceInputFailuresExplainHowToCorrectTheRequest(String code, String message) throws Exception {
        mvc.perform(get("/test-only/business/{code}", code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message));
    }

    static Stream<Arguments> invalidReferenceMessages() {
        return Stream.of(
                Arguments.of("INVALID_TIMEZONE", "Choisissez un fuseau horaire reconnu, par exemple Europe/Brussels."),
                Arguments.of("UNAVAILABILITY_TARGET_REQUIRED", "Choisissez une seule cible pour l’indisponibilité : une zone ou un véhicule.")
        );
    }

    @ParameterizedTest
    @MethodSource("businessMessages")
    void businessFailuresExposeStableCodeFrenchMessageAndConflictStatus(String code, String message) throws Exception {
        var result = mvc.perform(get("/test-only/business/{code}", code))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.validationErrors").isEmpty())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("stackTrace", "Exception", "detail technique privé");
    }

    @ParameterizedTest
    @MethodSource("securityFailures")
    void authenticationAndAuthorizationFailuresHaveSafeFrenchMessages(String path, int statusCode, String code,
                                                                       String message) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message));
    }

    static Stream<Arguments> securityFailures() {
        return Stream.of(
                Arguments.of("/test-only/unauthorized", 401, "UNAUTHORIZED",
                        ApiErrorResponse.unauthorized("/").message()),
                Arguments.of("/test-only/forbidden", 403, "FORBIDDEN",
                        ApiErrorResponse.forbidden("/").message())
        );
    }
}
