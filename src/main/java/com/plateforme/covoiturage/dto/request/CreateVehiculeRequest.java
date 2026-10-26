package com.plateforme.covoiturage.dto.request;
import jakarta.validation.constraints.*;
/** Aucun champ de propriété ou transfert de zone n'est introduit. */
public record CreateVehiculeRequest(@NotNull @Positive Long zoneId) {}
