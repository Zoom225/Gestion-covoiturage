package com.plateforme.covoiturage.controller;

import com.plateforme.covoiturage.dto.request.CompteSearchRequest;
import com.plateforme.covoiturage.dto.response.ApiErrorResponse;
import com.plateforme.covoiturage.dto.response.CompteAdministrationResponse;
import com.plateforme.covoiturage.dto.response.ComptePageResponse;
import com.plateforme.covoiturage.service.CompteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@RequestMapping("/api/admin/comptes")
@RequiredArgsConstructor
@Tag(name = "Comptes", description = "Consultation administrative dans le périmètre autorisé")
@SecurityRequirement(name = "bearerJwt")
@ApiResponse(responseCode = "400", description = "Paramètres invalides",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "401", description = "Authentification requise ou invalide",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "Permission administrative requise",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class CompteController {
    private final CompteService comptes;

    @GetMapping
    @Operation(summary = "Consulter les comptes",
            description = "GLOBAL : réseau ; LOCAL : clients de sa zone. Pagination indexée à 0, taille 20 par défaut (1 à 100), ordre compteId croissant. Recherche exacte sans casse et filtres par intersection.")
    @ApiResponse(responseCode = "200", description = "Page de comptes et total dans le même périmètre")
    public ResponseEntity<ComptePageResponse> search(
            @Parameter(hidden = true) Authentication actor,
            @Valid @ModelAttribute @ParameterObject CompteSearchRequest search) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(comptes.search(actor, search));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter un compte",
            description = "Même périmètre que la liste. Un compte absent ou invisible produit le même 404 générique.")
    @ApiResponse(responseCode = "200", description = "Compte autorisé, sans données sensibles")
    @ApiResponse(responseCode = "404", description = "Ressource introuvable",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<CompteAdministrationResponse> findById(
            @Parameter(hidden = true) Authentication actor, @Positive @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(comptes.findById(actor, id));
    }
}
