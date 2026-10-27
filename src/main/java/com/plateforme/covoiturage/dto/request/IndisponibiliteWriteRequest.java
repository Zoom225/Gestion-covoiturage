package com.plateforme.covoiturage.dto.request;
import jakarta.validation.constraints.*;
import java.time.Instant;
public record IndisponibiliteWriteRequest(@Positive Long zoneId, @Positive Long vehiculeId,
    /**
     * Exige exactement une cible, zone ou véhicule, sans autoriser un changement implicite de cible.
     */
        @NotNull Instant debut, @NotNull Instant fin) {
    @AssertTrue(message="Une seule cible zone ou véhicule est requise")
    public boolean isCibleValide() { return (zoneId == null) != (vehiculeId == null); }
    /**
     * Vérifie que la fin suit strictement le début, en laissant les contraintes NotNull traiter les dates absentes.
     */
    @AssertTrue(message="La fin doit être strictement après le début")
    public boolean isPeriodeValide() { return debut == null || fin == null || fin.isAfter(debut); }
}
