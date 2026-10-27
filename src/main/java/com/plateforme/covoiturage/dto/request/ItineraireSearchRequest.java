package com.plateforme.covoiturage.dto.request;

import jakarta.validation.constraints.Size;

public record ItineraireSearchRequest(@Size(max=255) String origine, @Size(max=255) String destination) {
    /** Normalise les filtres facultatifs sans perdre les accents ni tronquer une recherche trop longue. */
    public ItineraireSearchRequest {
        origine=origine==null?"":origine.strip();
        destination=destination==null?"":destination.strip();
    }
}
