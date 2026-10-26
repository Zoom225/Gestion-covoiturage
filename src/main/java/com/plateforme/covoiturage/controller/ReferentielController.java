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
@Tag(name="Référentiels",description="Consultation et gestion administrative des zones, véhicules et périodes")
@SecurityRequirement(name="bearerJwt")
@ApiResponse(responseCode="400",description="Paramètre invalide",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="401",description="Authentification requise",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="403",description="Permission administrative requise",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="404",description="Ressource absente ou invisible",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="409",description="Conflit métier ou intégrité",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
public class ReferentielController {
    private final ReferentielService service;

    @GetMapping("/statistiques")
    @Operation(summary="Consulter les statistiques administratives",description="Cumuls persistés par état et montants EUR séparés places/dettes. GLOBAL réseau ou zone filtrée, LOCAL uniquement sa zone ; le filtre ne peut étendre le périmètre. Aucun filtre temporel implicite.")
    public ResponseEntity<AdminStatisticsResponse> statistics(@Parameter(hidden=true) Authentication actor,
            @Positive @RequestParam(required=false) Long zoneId){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.statistics(actor,zoneId));
    }

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

    @PostMapping("/vehicules")
    @Operation(summary="Créer un véhicule dans une zone autorisée",description="GLOBAL réseau, LOCAL sa zone. Véhicule actif à la création ; aucun transfert ni propriétaire CLIENT.")
    @ApiResponse(responseCode="201",description="Véhicule créé")
    public ResponseEntity<VehiculeResponse> createVehicle(@Parameter(hidden=true) Authentication actor,
            @Valid @RequestBody com.plateforme.covoiturage.dto.request.CreateVehiculeRequest request) {
        var result=service.createVehicle(actor,request);
        return ResponseEntity.created(java.net.URI.create("/api/admin/vehicules/"+result.vehiculeId()))
            .cacheControl(CacheControl.noStore()).body(result);
    }
    @PatchMapping("/vehicules/{id}/statut")
    @Operation(summary="Activer ou désactiver un véhicule",description="GLOBAL réseau, LOCAL sa zone. La désactivation interdit les nouvelles planifications sans annuler les trajets existants.")
    public ResponseEntity<VehiculeResponse> vehicleStatus(@Parameter(hidden=true) Authentication actor,@Positive @PathVariable long id,
            @Valid @RequestBody com.plateforme.covoiturage.dto.request.VehiculeStatusRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.vehicleStatus(actor,id,request.actif()));
    }
    @PostMapping("/indisponibilites")
    @Operation(summary="Créer une indisponibilité de zone ou véhicule",description="Cible exclusive dans le périmètre administratif ; intervalle UTC [début,fin), rejet si un trajet existant est touché.")
    @ApiResponse(responseCode="201",description="Indisponibilité créée")
    public ResponseEntity<IndisponibiliteResponse> createPeriod(@Parameter(hidden=true) Authentication actor,
            @Valid @RequestBody com.plateforme.covoiturage.dto.request.IndisponibiliteWriteRequest request) {
        var result=service.createPeriod(actor,request);
        return ResponseEntity.created(java.net.URI.create("/api/admin/indisponibilites/"+result.indisponibiliteId()))
            .cacheControl(CacheControl.noStore()).body(result);
    }
    @PutMapping("/indisponibilites/{id}")
    @Operation(summary="Modifier les dates d'une indisponibilité",description="Cible conservée ; GLOBAL réseau, LOCAL sa zone ; rejet si conflit avec un trajet existant.")
    public ResponseEntity<IndisponibiliteResponse> updatePeriod(@Parameter(hidden=true) Authentication actor,@Positive @PathVariable long id,
            @Valid @RequestBody com.plateforme.covoiturage.dto.request.IndisponibiliteWriteRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.updatePeriod(actor,id,request));
    }
    @DeleteMapping("/indisponibilites/{id}")
    @Operation(summary="Retirer une indisponibilité dans son périmètre",description="Aucune suppression de véhicule, trajet, paiement ou dette.")
    public ResponseEntity<Void> deletePeriod(@Parameter(hidden=true) Authentication actor,@Positive @PathVariable long id) {
        service.deletePeriod(actor,id);return ResponseEntity.noContent().build();
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
