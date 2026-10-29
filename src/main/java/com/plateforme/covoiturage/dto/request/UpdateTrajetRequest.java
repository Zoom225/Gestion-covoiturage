package com.plateforme.covoiturage.dto.request;

import jakarta.validation.constraints.*;
import java.time.Instant;

/** La zone, le type, l'organisateur et les statuts ne sont pas modifiables. */
public record UpdateTrajetRequest(@NotNull @Positive Long vehiculeId,
 @NotNull Instant depart,@NotNull Instant arrivee,
 @NotBlank @Size(max=255) String origine,@NotBlank @Size(max=255) String destination) {
 public UpdateTrajetRequest {
  origine=origine==null?null:origine.strip();
  destination=destination==null?null:destination.strip();
 }
 @AssertTrue(message="L'arrivée doit être strictement postérieure au départ.")
 @com.fasterxml.jackson.annotation.JsonIgnore
 public boolean isIntervalValid(){return depart==null||arrivee==null||arrivee.isAfter(depart);}
}
