package com.plateforme.covoiturage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 100) String identifiant,
        @NotBlank @Size(max = 72) String password) {
    // Ne jamais journaliser le mot de passe, même via le toString d'un record.
    @Override
    public String toString() {
        return "LoginRequest[credentials=REDACTED]";
    }
}
