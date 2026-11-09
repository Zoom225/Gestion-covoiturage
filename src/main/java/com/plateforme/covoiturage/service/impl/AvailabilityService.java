package com.plateforme.covoiturage.service.impl;

import com.plateforme.covoiturage.entity.Vehicule;
import com.plateforme.covoiturage.exception.BusinessRuleException;
import com.plateforme.covoiturage.repository.IndisponibiliteRepository;
import com.plateforme.covoiturage.repository.TrajetRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Disponibilité des zones et véhicules ; écritures sous les verrous de JourneyLocks. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvailabilityService {
    private static final Duration VEHICLE_BREAK = Duration.ofMinutes(15);

    private final IndisponibiliteRepository periods;
    private final TrajetRepository trips;

    /** D2, RM19 : un intervalle valide ne peut chevaucher une indisponibilité. */
    public void requireAvailable(Vehicule vehicle, Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new BusinessRuleException("INVALID_INTERVAL");
        }
        if (periods.overlaps(vehicle.getZone().getId(), vehicle.getId(), start, end)) {
            throw new BusinessRuleException("RESOURCE_UNAVAILABLE");
        }
    }

    /** RM20 : prévoir quinze minutes de pause ; en modification, exclure le trajet lui-même. */
    public void requireAvailableForPlanning(Vehicule vehicle, Instant start, Instant end, Long existingTripId) {
        requireAvailable(vehicle, start, end);
        Instant occupiedFrom = start.minus(VEHICLE_BREAK);
        Instant occupiedUntil = end.plus(VEHICLE_BREAK);
        boolean conflict = existingTripId == null
                ? trips.overlaps(vehicle.getId(), occupiedFrom, occupiedUntil)
                : trips.overlapsExceptTrip(existingTripId, vehicle.getId(), occupiedFrom, occupiedUntil);
        if (conflict) {
            throw new BusinessRuleException("VEHICLE_CONFLICT");
        }
    }
}
