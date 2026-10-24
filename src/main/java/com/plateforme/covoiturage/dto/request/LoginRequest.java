package com.plateforme.covoiturage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(
        @Schema(description = "E-mail ADMIN ou matricule CLIENT ; comparaison insensible à la casse, maximum 100 caractères")
        @NotBlank @Size(max = 100) String identifiant,
        @Schema(description = "Mot de passe du compte, limité à 72 octets UTF-8", format = "password",
                accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(max = 72) String password) {
    // Ne jamais journaliser le mot de passe, même via le toString d'un record.
    @Override
    public String toString() {
        return "LoginRequest[credentials=REDACTED]";
    }
}
