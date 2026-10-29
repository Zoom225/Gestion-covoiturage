package com.plateforme.covoiturage.service;
import com.plateforme.covoiturage.dto.request.*;
import com.plateforme.covoiturage.dto.response.*;
import com.plateforme.covoiturage.entity.*;
import com.plateforme.covoiturage.enums.*;
import com.plateforme.covoiturage.exception.*;
import com.plateforme.covoiturage.mapper.TrajetMapper;
import com.plateforme.covoiturage.repository.*;
import com.plateforme.covoiturage.security.ClientAuthorization;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.time.Clock;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
@Service @RequiredArgsConstructor @lombok.extern.slf4j.Slf4j @Transactional(readOnly=true)
public class TrajetService {
 private final ClientAuthorization authorization;
 private final JourneyLocks locks;
 private final BookingPolicy policy;
 private final AvailabilityService availability;
 private final TrajetRepository trips;
 private final ReservationRepository bookings;
 private final ZoneAccessRepository zoneAccess;
 private final TrajetMapper mapper;
 private final Clock clock;
 private final TransactionTemplate transactions;
 private final SensitiveOperationJournal journal;
 private final J1ProcessingService j1;
 private final VehiculeRepository vehicles;
 private final PaiementPlaceRepository payments;

 /** Modifie uniquement un trajet encore libre d'engagement, dans la transaction des contrôles. */
 @Transactional
 public TrajetResponse update(Authentication actor,long id,UpdateTrajetRequest request){
  var requester=authorization.require(actor);
  var trip=locks.tripForUpdate(id,request.vehiculeId());
  if(!trip.getOrganisateur().getId().equals(requester.getId()))
   throw new org.springframework.security.access.AccessDeniedException("Acces interdit");
  policy.requireModifiable(trip,request.depart());
  if(payments.existsByReservationTrajetId(id))throw new BusinessRuleException("TRIP_HAS_PAYMENT");
  var vehicle=vehicles.findById(request.vehiculeId()).orElseThrow(ResourceNotFoundException::new);
  if(!vehicle.isActif())throw new BusinessRuleException("VEHICLE_INACTIVE");
  var pending=bookings.findByTrajetIdAndStatut(id,StatutReservation.EN_ATTENTE);
  if(!pending.isEmpty()&&(!java.util.Objects.equals(trip.getOrigine(),request.origine())
    ||!java.util.Objects.equals(trip.getDestination(),request.destination())))
   throw new BusinessRuleException("TRIP_LOCATIONS_LOCKED");
  // Même ordre partagé pour les clients ; aucune réservation n'est migrée ni annulée.
  var clientIds=new java.util.TreeSet<Long>();
  clientIds.add(requester.getId());
  pending.forEach(booking->clientIds.add(booking.getClient().getId()));
  for(Long clientId:clientIds){
   var client=locks.client(clientId);
   if(clientId.equals(requester.getId()))policy.eligible(client,trip.getZone(),request.depart());
   else requirePendingCompatible(client,trip,request);
  }
  availability.requireAvailable(vehicle,request.depart(),request.arrivee());
  if(trips.overlapsExceptTrip(id,vehicle.getId(),request.depart().minusSeconds(900),request.arrivee().plusSeconds(900)))
   throw new BusinessRuleException("VEHICLE_CONFLICT");
  trip.update(vehicle,request.depart(),request.arrivee(),request.origine(),request.destination());
  trips.flush();
  journal.record(SensitiveOperationJournal.Action.TRAJET_MODIFIE,actor,SensitiveOperationJournal.Resource.TRAJET,id);
  return mapper.trajet(trip);
 }
 private void requirePendingCompatible(Client client,Trajet trip,UpdateTrajetRequest request){
  try {policy.eligible(client,trip.getZone(),request.depart());}
  catch(BusinessRuleException|org.springframework.security.access.AccessDeniedException failure){
   throw new BusinessRuleException("PENDING_RESERVATION_INCOMPATIBLE");
  }
 }

 /** Parcourt aussi les arrivées anciennes ; chaque trajet dispose de sa propre transaction, comme à J-1. */
 @Transactional(propagation=Propagation.NOT_SUPPORTED)
 public int terminerTrajetsArrives(){
  // Rattraper J-1 avant REALISE, même au premier passage après un redémarrage.
  j1.catchUp();
  int processed=0;
  long after=0;
  var now=clock.instant();
  while(true){
   var candidates=trips.completionCandidates(after,now,PageRequest.of(0,100));
   if(candidates.isEmpty())return processed;
   for(Long id:candidates){
    after=id;
    try {
     if(Boolean.TRUE.equals(transactions.execute(status->terminerTrajet(id))))processed++;
    } catch(RuntimeException failure){
     log.warn("Fin de trajet : traitement du trajet {} à reprendre",id);
    }
   }
  }
 }

 private boolean terminerTrajet(long id){
  var trip=locks.trip(id);
  if(trip.getStatut()!=StatutTrajet.PLANIFIE&&trip.getStatut()!=StatutTrajet.COMPLET)return false;
  if(trip.getType()==TypeTrajet.PRIVE&&trip.getTraiteJ1Le()==null)return false;
  if(bookings.countByTrajetIdAndStatut(id,StatutReservation.CONFIRMEE)==0)return false;
  policy.requireAfterArrivalForCompletion(trip);
  for(var booking:bookings.findByTrajetIdAndStatut(id,StatutReservation.EN_ATTENTE)){
   booking.cancel(clock.instant());
   journal.record(SensitiveOperationJournal.Action.RESERVATION_ANNULEE,null,
    SensitiveOperationJournal.Resource.RESERVATION,booking.getId());
  }
  trip.markCompleted();
  trips.flush();
  journal.record(SensitiveOperationJournal.Action.TRAJET_REALISE,null,SensitiveOperationJournal.Resource.TRAJET,id);
  return true;
 }

 /**
  * Crée un trajet éligible et la réservation EN_ATTENTE de l'organisateur après contrôle du véhicule, des indisponibilités et de la pause de quinze minutes (RM14, RM19–20, D2–D4).
  */
 @Transactional
 public TrajetResponse create(Authentication actor,CreateTrajetRequest request){
  var requester=authorization.require(actor);
  var zone=locks.zone(request.zoneId());var vehicle=locks.vehicle(request.vehiculeId());
  if(!vehicle.getZone().getId().equals(zone.getId()))throw new BusinessRuleException("VEHICLE_ZONE_MISMATCH");
  if(!vehicle.isActif())throw new BusinessRuleException("VEHICLE_INACTIVE");
  var client=locks.client(requester.getId());
  policy.eligible(client,zone,request.depart());
  availability.requireAvailable(vehicle,request.depart(),request.arrivee());
  if(trips.overlaps(vehicle.getId(),request.depart().minusSeconds(900),request.arrivee().plusSeconds(900)))
   throw new BusinessRuleException("VEHICLE_CONFLICT");
  var trip=trips.saveAndFlush(new Trajet(zone,vehicle,client,request.depart(),request.arrivee(),request.type(),
   request.origine(),request.destination()));
  bookings.saveAndFlush(new Reservation(trip,client)); // logical first place, never confirmed here
  return mapper.trajet(trip);
 }
 /**
  * Liste uniquement les trajets publics autorisés et les trajets privés organisés ou rejoints par le client, avec filtrage avant pagination.
  */
 public ReferencePageResponse<TrajetResponse> list(Authentication actor,ReferencePageRequest page,Long zoneId){
  var client=authorization.require(actor);
  List<Long> allowed=zoneAccess.allowedZones(client.getId());
  Specification<Trajet> scope=(root,query,cb)->{
   var sub=query.subquery(Long.class);var booking=sub.from(Reservation.class);
   sub.select(booking.get("id")).where(cb.equal(booking.get("trajet").get("id"),root.get("id")),
    cb.equal(booking.get("client").get("id"),client.getId()),
    cb.notEqual(booking.get("statut"),StatutReservation.ANNULEE));
   var visibility=cb.or(cb.equal(root.get("type"),TypeTrajet.PUBLIC),
    cb.equal(root.get("organisateur").get("id"),client.getId()),cb.exists(sub));
   var zones=root.get("zone").get("id").in(allowed);
   return zoneId==null?cb.and(zones,visibility):cb.and(zones,visibility,cb.equal(root.get("zone").get("id"),zoneId));
  };
  return ReferencePageResponse.from(trips.findAll(scope,PageRequest.of(page.page(),page.size(),Sort.by("id"))).map(mapper::trajet));
 }
 /**
  * Retourne un trajet visible dans une zone autorisée, sans révéler un trajet privé auquel le client n'a plus accès.
  */
 public TrajetResponse detail(Authentication actor,long id){
  var client=authorization.require(actor);
  var trip=trips.findById(id).orElseThrow(ResourceNotFoundException::new);
  if(!zoneAccess.allowedZones(client.getId()).contains(trip.getZone().getId())
    ||(trip.getType()==TypeTrajet.PRIVE&&!trip.getOrganisateur().getId().equals(client.getId())
      &&!bookings.existsByTrajetIdAndClientIdAndStatutNot(id,client.getId(),StatutReservation.ANNULEE)))
   throw new ResourceNotFoundException();
  return mapper.trajet(trip);
 }
}
