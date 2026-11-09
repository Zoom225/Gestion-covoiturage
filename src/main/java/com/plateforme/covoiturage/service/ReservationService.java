package com.plateforme.covoiturage.service;

import com.plateforme.covoiturage.dto.request.ReferencePageRequest;
import com.plateforme.covoiturage.dto.response.ReferencePageResponse;
import com.plateforme.covoiturage.dto.response.ReservationResponse;
import org.springframework.security.core.Authentication;

/**
 * Reservations des quatre passagers, organisateur compris.
 * Le chauffeur est prevu par la flotte et n'occupe aucune de ces places.
 */
public interface ReservationService {
    /** Cree une demande personnelle EN_ATTENTE pour un trajet public. */
    ReservationResponse reserve(Authentication actor, long tripId);

    /** Permet a l'organisateur d'ajouter un participant eligible sur son trajet prive. */
    ReservationResponse addPrivate(Authentication actor, long tripId, long clientId);

    /** Annule sa propre reservation avant le depart, en conservant l'historique financier. */
    ReservationResponse cancel(Authentication actor, long reservationId);

    /** Consulte uniquement les reservations du client authentifie, annulations comprises. */
    ReferencePageResponse<ReservationResponse> mine(Authentication actor, ReferencePageRequest page);
}
