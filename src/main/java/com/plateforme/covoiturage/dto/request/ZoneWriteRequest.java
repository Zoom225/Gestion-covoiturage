package com.plateforme.covoiturage.dto.request;
import jakarta.validation.constraints.*;
public record ZoneWriteRequest(@NotBlank @Size(max=200) String nom,
 @NotBlank @Size(max=80) String fuseauHoraire, @NotNull Boolean ouverteOccasionnels) {}
