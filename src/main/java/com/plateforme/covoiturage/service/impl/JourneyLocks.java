package com.plateforme.covoiturage.service.impl;

import com.plateforme.covoiturage.entity.Client;
import com.plateforme.covoiturage.entity.Trajet;
import com.plateforme.covoiturage.entity.Vehicule;
import com.plateforme.covoiturage.entity.Zone;
import com.plateforme.covoiturage.exception.BusinessRuleException;
import com.plateforme.covoiturage.exception.ResourceNotFoundException;
import com.plateforme.covoiturage.repository.ClientRepository;
import com.plateforme.covoiturage.repository.TrajetRepository;
import com.plateforme.covoiturage.repository.VehiculeRepository;
import com.plateforme.covoiturage.repository.ZoneRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Protège les écritures concurrentes dans la transaction du service appelant.
 * Toujours verrouiller dans cet ordre : zone, véhicules par ID, trajet, client.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class JourneyLocks {
    private final ZoneRepository zones;
    private final VehiculeRepository vehicles;
    private final TrajetRepository trips;
    private final ClientRepository clients;
    private final EntityManager entityManager;

    public Zone zone(long id) {
        var zone = zones.lockById(id).orElseThrow(ResourceNotFoundException::new);
        entityManager.refresh(zone);
        return zone;
    }

    public Vehicule vehicle(long id) {
        vehicles.lock(id).orElseThrow(ResourceNotFoundException::new);
        var vehicle = vehicles.findById(id).orElseThrow(ResourceNotFoundException::new);
        entityManager.refresh(vehicle);
        return vehicle;
    }

    public Trajet trip(long id) {
        return lockTrip(id, null);
    }

    public Trajet tripForUpdate(long id, long newVehicleId) {
        return lockTrip(id, newVehicleId);
    }

    private Trajet lockTrip(long id, Long newVehicleId) {
        var trip = trips.findById(id).orElseThrow(ResourceNotFoundException::new);
        zone(trip.getZone().getId());
        // Une autre transaction a pu changer le véhicule pendant l'attente du verrou.
        entityManager.refresh(trip);
        long currentVehicleId = trip.getVehicule().getId();
        long targetVehicleId = newVehicleId == null ? currentVehicleId : newVehicleId;
        var selectedVehicle = vehicles.findById(targetVehicleId).orElseThrow(ResourceNotFoundException::new);
        if (!selectedVehicle.getZone().getId().equals(trip.getZone().getId())) {
            throw new BusinessRuleException("VEHICLE_ZONE_MISMATCH");
        }

        // L'ordre croissant évite que deux changements de véhicule se bloquent mutuellement.
        if (currentVehicleId == targetVehicleId) {
            vehicle(currentVehicleId);
        } else {
            vehicle(Math.min(currentVehicleId, targetVehicleId));
            vehicle(Math.max(currentVehicleId, targetVehicleId));
        }
        trips.lock(id).orElseThrow(ResourceNotFoundException::new);
        entityManager.refresh(trip);
        return trip;
    }

    public Client client(long id) {
        var client = clients.lockById(id).orElseThrow(ResourceNotFoundException::new);
        entityManager.refresh(client);
        return client;
    }
}
