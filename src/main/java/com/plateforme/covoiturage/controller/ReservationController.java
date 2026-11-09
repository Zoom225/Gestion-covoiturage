package com.plateforme.covoiturage.controller;

import com.plateforme.covoiturage.dto.request.PrivateParticipantRequest;
import com.plateforme.covoiturage.dto.request.ReferencePageRequest;
import com.plateforme.covoiturage.dto.response.ReferencePageResponse;
import com.plateforme.covoiturage.dto.response.ReservationResponse;
import com.plateforme.covoiturage.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Tag(name = "Reservations", description = "Quatre passagers par trajet, chauffeur fourni par la flotte en plus.")
@SecurityRequirement(name = "bearerJwt")
public class ReservationController {
    private final ReservationService reservations;

    @PostMapping("/trajets/{id}/reservations")
    @ApiResponse(responseCode = "201", description = "Demande EN_ATTENTE creee")
    @Operation(summary = "Demander sa reservation publique EN_ATTENTE",
            description = "Aucun paiement ni confirmation dans ce parcours.")
    public ResponseEntity<ReservationResponse> reserve(
            @Parameter(hidden = true) Authentication actor, @Positive @PathVariable long id) {
        return ResponseEntity.status(201).body(reservations.reserve(actor, id));
    }

    @PostMapping("/trajets/{id}/participants")
    @ApiResponse(responseCode = "201", description = "Participant EN_ATTENTE ajoute")
    @Operation(summary = "Ajouter un participant prive EN_ATTENTE (organisateur uniquement)")
    public ResponseEntity<ReservationResponse> participant(
            @Parameter(hidden = true) Authentication actor, @Positive @PathVariable long id,
            @Valid @RequestBody PrivateParticipantRequest request) {
        return ResponseEntity.status(201).body(reservations.addPrivate(actor, id, request.clientId()));
    }

    @GetMapping("/reservations/me")
    @Operation(summary = "Consulter uniquement ses reservations")
    public ReferencePageResponse<ReservationResponse> mine(
            @Parameter(hidden = true) Authentication actor,
            @Valid @ModelAttribute @ParameterObject ReferencePageRequest page) {
        return reservations.mine(actor, page);
    }

    @PostMapping("/reservations/{id}/annulation")
    @Operation(summary = "Annuler sa reservation avant le depart",
            description = "Le propriétaire seul peut annuler avant le départ. Aucun remboursement si une pénalité "
                    + "est active au moment de l’annulation, même si elle est réglée ensuite. Sans pénalité, une place "
                    + "confirmée est remboursée en simulation à H-24 ou avant. Paiements et dettes sont conservés.")
    public ResponseEntity<ReservationResponse> cancelBooking(
            @Parameter(hidden = true) Authentication actor, @Positive @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reservations.cancel(actor, id));
    }
}
