package com.plateforme.covoiturage.mapper;

import com.plateforme.covoiturage.dto.response.ReservationResponse;
import com.plateforme.covoiturage.entity.Reservation;
import org.springframework.stereotype.Component;

/** Conversion des reservations vers le contrat HTTP, sans regle metier. */
@Component
public class ReservationMapper {

    public ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getTrajet().getId(),
                reservation.getClient().getId(),
                reservation.getStatut(),
                reservation.getTrajet().getOrganisateur().getId().equals(reservation.getClient().getId()),
                reservation.getAnnuleLe(),
                reservation.isAnnuleAvecPenalite());
    }
}
