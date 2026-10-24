package com.plateforme.covoiturage.controller;

import com.plateforme.covoiturage.dto.request.LoginRequest;
import com.plateforme.covoiturage.dto.response.ProfileResponse;
import com.plateforme.covoiturage.dto.response.TokenResponse;
import com.plateforme.covoiturage.security.ComptePrincipal;
import com.plateforme.covoiturage.service.AuthService;
import com.plateforme.covoiturage.dto.response.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Connexion et consultation du profil authentifié")
public class AuthController {
    private final AuthService auth;

    @PostMapping("/login")
    @Operation(summary = "Se connecter", description = "Connexion ADMIN par e-mail ou CLIENT par matricule, sans distinction de casse. Le rôle et l'état du compte sont lus en base avant émission du JWT Bearer.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Jeton délivré")
    @ApiResponse(responseCode = "400", description = "Données ou JSON invalides",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Identifiants incorrects ou compte indisponible",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Consulter son profil", description = "Le compte est identifié par le JWT puis relu en PostgreSQL ; aucun identifiant client n'est accepté.")
    @ApiResponse(responseCode = "200", description = "Profil du compte authentifié")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide, expiré ou compte indisponible",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Accès interdit",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<ProfileResponse> me(@Parameter(hidden = true) @AuthenticationPrincipal ComptePrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.me(principal));
    }
}
