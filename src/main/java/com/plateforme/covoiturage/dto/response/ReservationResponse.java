package com.plateforme.covoiturage.dto.response;
import com.plateforme.covoiturage.entity.enums.StatutReservation;
public record ReservationResponse(Long reservationId,Long trajetId,Long clientId,StatutReservation statut,
 boolean organisateur,java.time.Instant annuleLe,boolean annuleAvecPenalite) {}
