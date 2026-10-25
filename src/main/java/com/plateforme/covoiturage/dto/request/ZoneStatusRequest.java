package com.plateforme.covoiturage.dto.request;
import jakarta.validation.constraints.NotNull;
public record ZoneStatusRequest(@NotNull Boolean actif) {}
