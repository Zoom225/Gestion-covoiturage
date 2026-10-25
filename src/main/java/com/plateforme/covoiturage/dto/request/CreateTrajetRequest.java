package com.plateforme.covoiturage.dto.request;
import com.plateforme.covoiturage.enums.TypeTrajet;
import jakarta.validation.constraints.*;
import java.time.Instant;
public record CreateTrajetRequest(@NotNull @Positive Long zoneId,@NotNull @Positive Long vehiculeId,
 @NotNull Instant depart,@NotNull Instant arrivee,@NotNull TypeTrajet type) {
 @AssertTrue(message="L'arrivee doit suivre strictement le depart")
 @com.fasterxml.jackson.annotation.JsonIgnore
 public boolean isIntervalValid(){return depart==null||arrivee==null||arrivee.isAfter(depart);}
}
