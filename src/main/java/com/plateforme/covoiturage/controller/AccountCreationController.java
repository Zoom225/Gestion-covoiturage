package com.plateforme.covoiturage.controller;

import com.plateforme.covoiturage.dto.request.*;
import com.plateforme.covoiturage.dto.response.*;
import com.plateforme.covoiturage.service.CompteService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name="Comptes", description="Création administrative avec invitation sécurisée")
@SecurityRequirement(name="bearerJwt")
@ApiResponse(responseCode="400",description="Données ou champs interdits",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="401",description="Authentification requise",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="403",description="Opération ou périmètre interdit",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="404",description="Ressource introuvable",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="409",description="Identifiant ou état incompatible",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
@ApiResponse(responseCode="503",description="Configuration de livraison indisponible ; transaction annulée",content=@Content(schema=@Schema(implementation=ApiErrorResponse.class)))
public class AccountCreationController {
    private final CompteService comptes;

    @PostMapping("/clients")
    @Operation(summary="Créer un client invité",description="GLOBAL : VIP, LOCAL, OCCASIONNEL. LOCAL : uniquement LOCAL dans sa zone. Matricule et activités imposés côté serveur ; aucun mot de passe fourni.")
    @ApiResponse(responseCode="201",description="Compte INVITE créé et invitation mise en file")
    public ResponseEntity<CompteInvitationResponse> client(@Parameter(hidden=true) Authentication actor,
            @Valid @RequestBody CreateClientRequest request) {
        return created(comptes.createClient(actor,request));
    }

    @PostMapping("/administrateurs")
    @Operation(summary="Créer un administrateur invité",description="GLOBAL uniquement. E-mail canonique ; zone obligatoire pour LOCAL, absente pour GLOBAL. Aucun mot de passe ni jeton retourné.")
    @ApiResponse(responseCode="201",description="Compte INVITE créé et invitation mise en file")
    public ResponseEntity<CompteInvitationResponse> administrateur(@Parameter(hidden=true) Authentication actor,
            @Valid @RequestBody CreateAdministrateurRequest request) {
        return created(comptes.createAdministrateur(actor,request));
    }

    private ResponseEntity<CompteInvitationResponse> created(CompteInvitationResponse result) {
        return ResponseEntity.created(URI.create("/api/admin/comptes/"+result.compte().compteId()))
                .cacheControl(CacheControl.noStore()).body(result);
    }
}
