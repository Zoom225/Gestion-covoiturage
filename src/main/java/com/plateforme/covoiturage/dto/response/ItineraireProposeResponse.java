package com.plateforme.covoiturage.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description="Lieux proposés pour préremplir un formulaire ; aucune place ni disponibilité garantie.")
public record ItineraireProposeResponse(Long itineraireId, String origine, String destination) {}
