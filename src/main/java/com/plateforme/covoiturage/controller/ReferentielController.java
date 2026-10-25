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

    @PostMapping("/zones")
    @Operation(summary="Creer une zone (GLOBAL)")
    @ApiResponse(responseCode="201",description="Zone creee")
    public ResponseEntity<ZoneResponse> createZone(@Parameter(hidden=true) Authentication actor,
            @Valid @RequestBody com.plateforme.covoiturage.dto.request.ZoneWriteRequest request){
        var result=service.createZone(actor,request);
        return ResponseEntity.created(java.net.URI.create("/api/admin/zones/"+result.zoneId()))
            .cacheControl(CacheControl.noStore()).body(result);
    }
    @PutMapping("/zones/{id}")
    @Operation(summary="Configurer une zone (GLOBAL)",description="Nom, fuseau IANA et acces OCCASIONNEL ; aucun transfert implicite.")
    public ZoneResponse updateZone(@Parameter(hidden=true) Authentication actor,@Positive @PathVariable long id,
            @Valid @RequestBody com.plateforme.covoiturage.dto.request.ZoneWriteRequest request){
        return service.updateZone(actor,id,request);
    }
    @PatchMapping("/zones/{id}/statut")
    @Operation(summary="Activer ou desactiver une zone (GLOBAL)",description="Les relations sont conservees ; aucune suppression physique.")
    public ZoneResponse zoneStatus(@Parameter(hidden=true) Authentication actor,@Positive @PathVariable long id,
            @Valid @RequestBody com.plateforme.covoiturage.dto.request.ZoneStatusRequest request){
        return service.zoneStatus(actor,id,request.actif());
    }
    @PutMapping("/zones/{id}/clients-vip/{clientId}")
    @Operation(summary="Autoriser une zone a un VIP (GLOBAL)")
    public ResponseEntity<Void> grant(@Parameter(hidden=true) Authentication actor,@Positive @PathVariable long id,
            @Positive @PathVariable long clientId){
        service.vipAccess(actor,id,clientId,true);return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/zones/{id}/clients-vip/{clientId}")
    @Operation(summary="Retirer une autorisation de zone VIP (GLOBAL)")
    public ResponseEntity<Void> revoke(@Parameter(hidden=true) Authentication actor,@Positive @PathVariable long id,
            @Positive @PathVariable long clientId){
        service.vipAccess(actor,id,clientId,false);return ResponseEntity.noContent().build();
    }

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
