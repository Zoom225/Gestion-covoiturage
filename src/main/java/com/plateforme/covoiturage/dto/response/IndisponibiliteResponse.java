package com.plateforme.covoiturage.dto.response;
import java.time.Instant;
/** zoneId désigne la cible directe ; null lorsque la cible est le véhicule. */
public record IndisponibiliteResponse(Long indisponibiliteId,Long zoneId,Long vehiculeId,
                                      Instant debut,Instant fin) {}
