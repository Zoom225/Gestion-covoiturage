package com.plateforme.covoiturage.controller;
import com.plateforme.covoiturage.dto.request.ReferencePageRequest;
import com.plateforme.covoiturage.dto.response.*;
import com.plateforme.covoiturage.service.ReferentielService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api") @RequiredArgsConstructor
@Tag(name="Référentiels client",description="Catalogue de lieux de référence et lecture des zones et véhicules autorisés, sans mutation.")
@SecurityRequirement(name="bearerJwt")
public class ClientReferentielController {
 private final ReferentielService service;
 /** Consulte les lieux proposés au client authentifié sans créer de trajet ni modifier ses droits de zone. */
 @GetMapping("/itineraires")
 @Operation(summary="Rechercher les itinéraires proposés",description="Références communes aux clients authentifiés ; filtres partiels origine/destination sans casse, pagination existante. Aucun trajet réservé ni droit de zone accordé ; les lieux peuvent préremplir POST /api/trajets.")
 public org.springframework.http.ResponseEntity<ReferencePageResponse<ItineraireProposeResponse>> itineraires(
  @Parameter(hidden=true) Authentication actor,
  @Valid @ModelAttribute @ParameterObject ReferencePageRequest page,
  @Valid @ModelAttribute @ParameterObject com.plateforme.covoiturage.dto.request.ItineraireSearchRequest search){
  return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
   .body(service.clientItineraires(actor,page,search));
 }
 @GetMapping("/zones") @Operation(summary="Choisir parmi ses zones autorisees")
 public ReferencePageResponse<ZoneResponse> zones(@Parameter(hidden=true) Authentication actor,
  @Valid @ModelAttribute @ParameterObject ReferencePageRequest page){return service.clientZones(actor,page);}
 @GetMapping("/vehicules") @Operation(summary="Consulter les vehicules de ses zones autorisees")
 public ReferencePageResponse<VehiculeResponse> vehicles(@Parameter(hidden=true) Authentication actor,
  @Valid @ModelAttribute @ParameterObject ReferencePageRequest page,@Positive @RequestParam(required=false) Long zoneId){
  return service.clientVehicles(actor,page,zoneId);
 }
}
