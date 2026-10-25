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
@Tag(name="Referentiels client",description="Lecture des seules zones autorisees et de leur flotte, aucune mutation.")
@SecurityRequirement(name="bearerJwt")
public class ClientReferentielController {
 private final ReferentielService service;
 @GetMapping("/zones") @Operation(summary="Choisir parmi ses zones autorisees")
 public ReferencePageResponse<ZoneResponse> zones(@Parameter(hidden=true) Authentication actor,
  @Valid @ModelAttribute @ParameterObject ReferencePageRequest page){return service.clientZones(actor,page);}
 @GetMapping("/vehicules") @Operation(summary="Consulter les vehicules de ses zones autorisees")
 public ReferencePageResponse<VehiculeResponse> vehicles(@Parameter(hidden=true) Authentication actor,
  @Valid @ModelAttribute @ParameterObject ReferencePageRequest page,@Positive @RequestParam(required=false) Long zoneId){
  return service.clientVehicles(actor,page,zoneId);
 }
}
