package com.plateforme.covoiturage.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/** Conventions techniques de pagination identiques à la consultation des comptes. */
public record ReferencePageRequest(
        @Min(0) @Schema(defaultValue="0",minimum="0") Integer page,
        @Min(1) @Max(100) @Schema(defaultValue="20",minimum="1",maximum="100") Integer size) {
    public ReferencePageRequest {
        page=page==null?0:page;
        size=size==null?20:size;
    }
    @AssertTrue(message="Décalage de pagination trop élevé")
    @Schema(hidden=true)
    public boolean isOffsetSupported() { return (long)page*size<=Integer.MAX_VALUE; }
}
