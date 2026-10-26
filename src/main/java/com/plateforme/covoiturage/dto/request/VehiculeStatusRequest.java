package com.plateforme.covoiturage.dto.request;
import jakarta.validation.constraints.NotNull;
public record VehiculeStatusRequest(@NotNull Boolean actif) {}
