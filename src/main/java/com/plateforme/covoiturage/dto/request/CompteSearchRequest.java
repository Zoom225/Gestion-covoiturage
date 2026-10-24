package com.plateforme.covoiturage.dto.request;

import com.plateforme.covoiturage.enums.CategorieClient;
import com.plateforme.covoiturage.enums.RoleTechnique;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record CompteSearchRequest(
        @Min(0) @Schema(defaultValue = "0", minimum = "0") Integer page,
        @Min(1) @Max(100) @Schema(defaultValue = "20", minimum = "1", maximum = "100") Integer size,
        @Size(min = 1, max = 100) String identifiant,
        RoleTechnique role,
        CategorieClient categorie,
        @Positive Long zoneId,
        Boolean compteActif,
        Boolean clientActif) {

    public CompteSearchRequest {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }

    @AssertTrue(message = "Décalage de pagination trop élevé")
    @Schema(hidden = true)
    public boolean isOffsetSupported() {
        return (long) page * size <= Integer.MAX_VALUE;
    }
}
