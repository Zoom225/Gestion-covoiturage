package com.plateforme.covoiturage.service;
import com.plateforme.covoiturage.entity.*;
import com.plateforme.covoiturage.repository.*;
import com.plateforme.covoiturage.exception.ResourceNotFoundException;
import com.plateforme.covoiturage.exception.BusinessRuleException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
/** Shared order: zone -> vehicle -> trip -> client. Refresh after waiting for a database lock. */
@Component @RequiredArgsConstructor @Transactional(propagation=Propagation.MANDATORY)
public class JourneyLocks {
 private final ZoneRepository zones;
 private final VehiculeRepository vehicles;
 private final TrajetRepository trips;
 private final ClientRepository clients;
 private final EntityManager entityManager;
 /**
  * Verrouille et relit la zone pour sérialiser les mutations de flotte et de trajets dans l'ordre partagé zone, véhicule, trajet, client.
  */
 public Zone zone(long id){
  var z=zones.lockById(id).orElseThrow(ResourceNotFoundException::new);entityManager.refresh(z);return z;
 }
 /**
  * Verrouille puis relit le véhicule après l'éventuelle attente, afin de recontrôler sa disponibilité réelle.
  */
 public Vehicule vehicle(long id){
  vehicles.lock(id).orElseThrow(ResourceNotFoundException::new);
  var v=vehicles.findById(id).orElseThrow(ResourceNotFoundException::new);entityManager.refresh(v);return v;
 }
 /**
  * Acquiert les verrous de zone, véhicule et trajet puis relit le trajet pour sérialiser paiement, réservation et traitement J-1.
  */
 public Trajet trip(long id){
  return lockTrip(id,null);
 }
 /** Verrouille aussi le nouveau véhicule, dans l'ordre des identifiants, avant le trajet. */
 public Trajet tripForUpdate(long id,long vehicleId){
  return lockTrip(id,vehicleId);
 }
 private Trajet lockTrip(long id,Long vehicleId){
  var trip=trips.findById(id).orElseThrow(ResourceNotFoundException::new);
  zone(trip.getZone().getId());
  // Une modification peut avoir changé le véhicule pendant l'attente du verrou de zone.
  entityManager.refresh(trip);
  long current=trip.getVehicule().getId();
  long target=vehicleId==null?current:vehicleId;
  var selected=vehicles.findById(target).orElseThrow(ResourceNotFoundException::new);
  if(!selected.getZone().getId().equals(trip.getZone().getId()))
   throw new BusinessRuleException("VEHICLE_ZONE_MISMATCH");
  java.util.stream.LongStream.of(current,target).distinct().sorted().forEach(this::vehicle);
  trips.lock(id).orElseThrow(ResourceNotFoundException::new);entityManager.refresh(trip);return trip;
 }
 /**
  * Verrouille et relit le client avant de recontrôler les droits et dettes dans la transaction appelante.
  */
 public Client client(long id){
  var c=clients.lockById(id).orElseThrow(ResourceNotFoundException::new);entityManager.refresh(c);return c;
 }
}
