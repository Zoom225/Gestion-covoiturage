package com.plateforme.covoiturage.dto.response;
import com.plateforme.covoiturage.enums.StatutReservation;
public record ReservationResponse(Long reservationId,Long trajetId,Long clientId,StatutReservation statut,
 boolean organisateur) {}
