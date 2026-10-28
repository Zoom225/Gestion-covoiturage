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
 private final ReservationService bookings;
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
 @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201",description="Demande EN_ATTENTE créée")
 @PostMapping("/trajets/{id}/reservations") @Operation(summary="Demander sa réservation publique EN_ATTENTE",description="Aucun paiement ni confirmation simulés dans ce parcours.")
 public ResponseEntity<ReservationResponse> reserve(@Parameter(hidden=true) Authentication actor,@Positive @PathVariable long id){
  return ResponseEntity.status(201).body(bookings.reserve(actor,id));
 }
 @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="201",description="Participant EN_ATTENTE ajouté")
 @PostMapping("/trajets/{id}/participants") @Operation(summary="Ajouter un participant privé EN_ATTENTE (organisateur uniquement)")
 public ResponseEntity<ReservationResponse> participant(@Parameter(hidden=true) Authentication actor,@Positive @PathVariable long id,
  @Valid @RequestBody PrivateParticipantRequest request){return ResponseEntity.status(201).body(bookings.addPrivate(actor,id,request.clientId()));}
 @GetMapping("/reservations/me") @Operation(summary="Consulter uniquement ses réservations")
 public ReferencePageResponse<ReservationResponse> mine(@Parameter(hidden=true) Authentication actor,
  @Valid @ModelAttribute @ParameterObject ReferencePageRequest page){return bookings.mine(actor,page);}
 @PostMapping("/reservations/{id}/annulation")
 @Operation(summary="Annuler sa réservation avant le départ",description="Le propriétaire seul peut annuler. Une place confirmée est remboursée intégralement en simulation à H-24 ou avant. Paiements et dettes restent conservés. Le remboursement est consultable dans GET /api/paiements/me.")
 public ResponseEntity<ReservationResponse> cancelBooking(@Parameter(hidden=true) Authentication actor,
  @Positive @PathVariable long id){
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(bookings.cancel(actor,id));
 }
}
