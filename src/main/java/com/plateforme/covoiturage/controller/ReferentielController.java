package com.plateforme.covoiturage.controller;

import com.plateforme.covoiturage.dto.request.ReferencePageRequest;
import com.plateforme.covoiturage.dto.response.*;
import com.plateforme.covoiturage.service.ReferentielService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name="Référentiels",description="Consultation administrative des zones, véhicules et périodes")
@SecurityRequirement(name="bearerJwt")
@ApiResponse(responseCode="400",description="Paramètre invalide",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="401",description="Authentification requise",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="403",description="Permission administrative requise",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="404",description="Ressource absente ou invisible",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
public class ReferentielController {
    private final ReferentielService service;

    @GetMapping("/zones")
    @Operation(summary="Consulter les zones",description="Zones du réseau pour GLOBAL, zone propre pour LOCAL. Pagination 0/20/100, ordre ID croissant ; périmètre appliqué avant pagination et total.")
    @ApiResponse(responseCode="200",description="Page autorisée")
    public ResponseEntity<ReferencePageResponse<ZoneResponse>> zones(
            @Parameter(hidden=true) Authentication actor, @Valid @ModelAttribute @ParameterObject ReferencePageRequest page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.zones(actor,page));
    }

    @GetMapping("/zones/{id}")
    @Operation(summary="Consulter zone",description="Même 404 pour ressource absente ou hors périmètre.")
    @ApiResponse(responseCode="200",description="Référentiel autorisé")
    public ResponseEntity<ZoneResponse> zone(
            @Parameter(hidden=true) Authentication actor,@Positive @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.zone(actor,id));
    }

    @GetMapping("/vehicules")
    @Operation(summary="Consulter les vehicules",description="Véhicules de la flotte ; filtre zoneId intersecté avec le périmètre. Pagination 0/20/100, ordre ID croissant ; périmètre appliqué avant pagination et total.")
    @ApiResponse(responseCode="200",description="Page autorisée")
    public ResponseEntity<ReferencePageResponse<VehiculeResponse>> vehicules(
            @Parameter(hidden=true) Authentication actor, @Valid @ModelAttribute @ParameterObject ReferencePageRequest page, @Positive @RequestParam(required=false) Long zoneId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.vehicules(actor,page,zoneId));
    }

    @GetMapping("/vehicules/{id}")
    @Operation(summary="Consulter vehicule",description="Même 404 pour ressource absente ou hors périmètre.")
    @ApiResponse(responseCode="200",description="Référentiel autorisé")
    public ResponseEntity<VehiculeResponse> vehicule(
            @Parameter(hidden=true) Authentication actor,@Positive @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.vehicule(actor,id));
    }

    @GetMapping("/indisponibilites")
    @Operation(summary="Consulter les indisponibilites",description="Périodes enregistrées de zone ou véhicule ; aucun verdict de disponibilité ou droit de mutation. Pagination 0/20/100, ordre ID croissant ; périmètre appliqué avant pagination et total.")
    @ApiResponse(responseCode="200",description="Page autorisée")
    public ResponseEntity<ReferencePageResponse<IndisponibiliteResponse>> indisponibilites(
            @Parameter(hidden=true) Authentication actor, @Valid @ModelAttribute @ParameterObject ReferencePageRequest page, @Positive @RequestParam(required=false) Long zoneId, @Positive @RequestParam(required=false) Long vehiculeId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.indisponibilites(actor,page,zoneId,vehiculeId));
    }

    @GetMapping("/indisponibilites/{id}")
    @Operation(summary="Consulter indisponibilite",description="Même 404 pour ressource absente ou hors périmètre.")
    @ApiResponse(responseCode="200",description="Référentiel autorisé")
    public ResponseEntity<IndisponibiliteResponse> indisponibilite(
            @Parameter(hidden=true) Authentication actor,@Positive @PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.indisponibilite(actor,id));
    }
}
