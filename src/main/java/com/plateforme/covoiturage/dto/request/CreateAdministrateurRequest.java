package com.plateforme.covoiturage.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.plateforme.covoiturage.enums.PerimetreAdmin;
import jakarta.validation.constraints.*;

public record CreateAdministrateurRequest(
        @NotBlank @Email @Size(max=100) String email,
        @NotNull PerimetreAdmin perimetreAdmin,
        @Positive Long zoneAdminId) {
    @JsonAnySetter public void refuseUnknown(String name, Object value) { throw new IllegalArgumentException("Champ non autorisé"); }
    @Override public String toString() { return "CreateAdministrateurRequest[REDACTED]"; }
}
