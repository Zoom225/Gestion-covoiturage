package com.plateforme.covoiturage.service.impl;

import com.plateforme.covoiturage.entity.Client;
import com.plateforme.covoiturage.entity.Trajet;
import com.plateforme.covoiturage.entity.Zone;
import com.plateforme.covoiturage.entity.enums.StatutReservation;
import com.plateforme.covoiturage.entity.enums.StatutTrajet;
import com.plateforme.covoiturage.entity.enums.TypeTrajet;
import com.plateforme.covoiturage.exception.BusinessRuleException;
import com.plateforme.covoiturage.repository.DetteTrajetRepository;
import com.plateforme.covoiturage.repository.ReservationRepository;
import com.plateforme.covoiturage.repository.ZoneAccessRepository;
import org.springframework.security.access.AccessDeniedException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Règles communes à la création, la réservation, au paiement et au contrôle J-1. */
@Component
@RequiredArgsConstructor
public class BookingPolicy {
    /** D4 : quatre passagers payants, organisateur compris, plus le chauffeur de la flotte. */
    public static final int MAX_CONFIRMED_PASSENGERS = 4;

    static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Brussels");
    private static final LocalTime J1_CHECK_TIME = LocalTime.of(0, 15);

    private final ZoneAccessRepository zones;
    private final DetteTrajetRepository debts;
    private final ReservationRepository reservations;
    private final Clock clock;

    /** RM05–08, RM36–38 : zone autorisée, aucune dette active et horizon selon la catégorie. */
    public void requireEligible(Client client, Zone zone, Instant departure) {
        if (!permits(client, zone)) {
            throw new AccessDeniedException("Acces interdit");
        }
        if (zone.getFuseauHoraire() == null) {
            throw new BusinessRuleException("ZONE_TIMEZONE_REQUIRED");
        }
        if (hasActivePenalty(client.getId())) {
            throw new BusinessRuleException("ACTIVE_DEBT");
        }

        int advanceDays = switch (client.getCategorie()) {
            case VIP -> 30;
            case LOCAL -> 10;
            case OCCASIONNEL -> 3;
        };
        Instant now = clock.instant();
        Instant bookingOpensAt = departure.atZone(ZoneId.of(zone.getFuseauHoraire()))
                .minusDays(advanceDays).toInstant();
        if (!departure.isAfter(now)) {
            throw new BusinessRuleException("DEPARTURE_PASSED");
        }
        if (now.isBefore(bookingOpensAt)) {
            throw new BusinessRuleException("BOOKING_HORIZON");
        }
    }

    /** Une dette partiellement réglée reste une pénalité active. */
    public boolean hasActivePenalty(long clientId) {
        return debts.existsByClientIdAndStatut(clientId, "ACTIVE");
    }

    /** D3 : LOCAL dans sa zone, VIP sur autorisation, OCCASIONNEL dans une zone ouverte. */
    public boolean permits(Client client, Zone zone) {
        return client.isActif() && zone.isActif() && switch (client.getCategorie()) {
            case LOCAL -> client.getZone() != null && client.getZone().getId().equals(zone.getId());
            case VIP -> zones.exists(client.getId(), zone.getId());
            case OCCASIONNEL -> zone.isOuverteOccasionnels();
        };
    }

    /** D4 : seules les confirmations occupent une place ; EN_ATTENTE ne garantit rien. */
    public long requireOpen(Trajet trip) {
        if (trip.getStatut() != StatutTrajet.PLANIFIE) {
            throw new BusinessRuleException("TRIP_NOT_OPEN");
        }
        long confirmedPassengers = reservations.countByTrajetIdAndStatut(
                trip.getId(), StatutReservation.CONFIRMEE);
        if (confirmedPassengers >= MAX_CONFIRMED_PASSENGERS) {
            throw new BusinessRuleException("TRIP_FULL");
        }
        return confirmedPassengers;
    }

    /** Un trajet engagé ou soumis au contrôle J-1 ne peut plus être replanifié. */
    public void requireModifiable(Trajet trip, Instant newDeparture) {
        Instant now = clock.instant();
        if (!trip.getDepart().isAfter(now)) {
            throw new BusinessRuleException("TRIP_ALREADY_STARTED");
        }
        if (trip.getStatut() != StatutTrajet.PLANIFIE) {
            throw new BusinessRuleException("TRIP_NOT_MODIFIABLE");
        }
        if (trip.isVerificationJ1Requise()) {
            throw new BusinessRuleException("TRIP_J1_VERIFICATION_REQUIRED");
        }
        if (trip.getTraiteJ1Le() != null) {
            throw new BusinessRuleException("TRIP_J1_ALREADY_PROCESSED");
        }
        if (trip.getType() == TypeTrajet.PRIVE
                && (!now.isBefore(j1Deadline(trip.getDepart()))
                || !now.isBefore(j1Deadline(newDeparture)))) {
            throw new BusinessRuleException("TRIP_J1_DUE");
        }
        if (debts.existsByTrajetId(trip.getId())) {
            throw new BusinessRuleException("TRIP_HAS_DEBT");
        }
        if (reservations.countByTrajetIdAndStatut(trip.getId(), StatutReservation.CONFIRMEE) > 0) {
            throw new BusinessRuleException("TRIP_HAS_CONFIRMED_RESERVATION");
        }
    }

    /**
     * Garde du futur parcours d'annulation de trajet privé : ne pas contourner la dette J-1.
     * Le parcours actuel annule une réservation, pas le trajet ; il n'appelle pas cette garde.
     */
    public void requireCancellationPreservesJ1Debt(Trajet trip) {
        if (trip.getType() != TypeTrajet.PRIVE || trip.getStatut() != StatutTrajet.PLANIFIE) {
            return;
        }
        if (clock.instant().isBefore(j1Deadline(trip.getDepart()))
                && reservations.countByTrajetIdAndStatut(
                        trip.getId(), StatutReservation.CONFIRMEE) < MAX_CONFIRMED_PASSENGERS
                && !debts.existsByTrajetId(trip.getId())) {
            throw new BusinessRuleException("PRIVATE_CANCELLATION_AVOIDS_J1_DEBT");
        }
    }

    /** La fin de trajet est autorisée à partir de l'arrivée prévue, borne incluse. */
    public void requireAfterArrivalForCompletion(Trajet trip) {
        if (clock.instant().isBefore(trip.getArrivee())) {
            throw new BusinessRuleException("TRIP_ARRIVAL_NOT_PASSED");
        }
    }

    /** J-1 est la veille à 00:15 à Bruxelles ; ce n'est pas le départ moins 24 heures. */
    static Instant j1Deadline(Instant departure) {
        return departure.atZone(BUSINESS_ZONE).toLocalDate().minusDays(1)
                .atTime(J1_CHECK_TIME).atZone(BUSINESS_ZONE).toInstant();
    }
}
