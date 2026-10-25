package com.plateforme.covoiturage.dto.request;
import jakarta.validation.constraints.*;
public record PrivateParticipantRequest(@NotNull @Positive Long clientId) {}
