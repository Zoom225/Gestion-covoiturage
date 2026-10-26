package com.plateforme.covoiturage.dto.request;
import com.plateforme.covoiturage.enums.TypeTrajet;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
public record CreateTrajetRequest(@NotNull @Positive Long zoneId,@NotNull @Positive Long vehiculeId,
 @NotNull Instant depart,@NotNull Instant arrivee,@NotNull TypeTrajet type,
 @NotBlank @Size(max=255) @Schema(description="Libellé géographique du point de départ",example="Gare de Bruxelles-Midi") String origine,
 @NotBlank @Size(max=255) @Schema(description="Libellé géographique de la destination",example="Gare de Liège-Guillemins") String destination) {
 public CreateTrajetRequest {
  origine=origine==null?null:origine.strip();
  destination=destination==null?null:destination.strip();
 }
 @AssertTrue(message="L'arrivee doit suivre strictement le depart")
 @com.fasterxml.jackson.annotation.JsonIgnore
 public boolean isIntervalValid(){return depart==null||arrivee==null||arrivee.isAfter(depart);}
}
