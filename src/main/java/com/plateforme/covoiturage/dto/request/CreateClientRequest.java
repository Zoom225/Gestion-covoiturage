package com.plateforme.covoiturage.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.plateforme.covoiturage.enums.CategorieClient;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;

public record CreateClientRequest(
        @NotBlank @Email @Size(max=254) String email,
        @NotNull CategorieClient categorie,
        @Positive Long zoneClientId) {
    @JsonAnySetter public void refuseUnknown(String name, Object value) { throw new IllegalArgumentException("Champ non autorisé"); }
    @Override public String toString() { return "CreateClientRequest[REDACTED]"; }
}
