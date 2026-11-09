package com.plateforme.covoiturage.controller;
import com.plateforme.covoiturage.dto.request.*;
import com.plateforme.covoiturage.dto.response.*;
import com.plateforme.covoiturage.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api")
@Tag(name="Trajets et réservations",description="CLIENT authentifié ; zones et propriété vérifiées en base.")
@SecurityRequirement(name="bearerJwt")
public class TrajetController {
 private final TrajetService trips;

 @PutMapping("/trajets/{id}")
 @Operation(summary="Modifier son trajet avant confirmation et contrôle J-1")
 public ResponseEntity<TrajetResponse> update(@Parameter(hidden=true) Authentication actor,
  @Positive @PathVariable long id,@Valid @RequestBody UpdateTrajetRequest request){
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(trips.update(actor,id,request));
 }
 @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201",description="Trajet et place logique créés")
 @PostMapping("/trajets") @Operation(summary="Créer un trajet et la place logique EN_ATTENTE de l'organisateur")
 public ResponseEntity<TrajetResponse> create(@Parameter(hidden=true) Authentication actor,@Valid @RequestBody CreateTrajetRequest request){
  var result=trips.create(actor,request);
  return ResponseEntity.created(java.net.URI.create("/api/trajets/"+result.trajetId())).cacheControl(CacheControl.noStore()).body(result);
 }
 @GetMapping("/trajets") @Operation(summary="Consulter les trajets visibles et autorisés",description="Trajets publics autorisés et privés organisés ou rejoints par le client ; filtrage avant pagination et total.")
 public ReferencePageResponse<TrajetResponse> list(@Parameter(hidden=true) Authentication actor,
  @Valid @ModelAttribute @ParameterObject ReferencePageRequest page,@RequestParam(required=false) @Positive Long zoneId){
  return trips.list(actor,page,zoneId);
 }
 @GetMapping("/trajets/{id}") @Operation(summary="Consulter un trajet visible")
 public TrajetResponse detail(@Parameter(hidden=true) Authentication actor,@Positive @PathVariable long id){return trips.detail(actor,id);}
}
