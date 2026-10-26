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
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class TrajetService {
 private final ClientAuthorization authorization;
 private final JourneyLocks locks;
 private final BookingPolicy policy;
 private final AvailabilityService availability;
 private final TrajetRepository trips;
 private final ReservationRepository bookings;
 private final ZoneAccessRepository zoneAccess;
 private final TrajetMapper mapper;

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
 public ReferencePageResponse<TrajetResponse> list(Authentication actor,ReferencePageRequest page,Long zoneId){
  var client=authorization.require(actor);
  List<Long> allowed=zoneAccess.allowedZones(client.getId());
  Specification<Trajet> scope=(root,query,cb)->{
   var sub=query.subquery(Long.class);var booking=sub.from(Reservation.class);
   sub.select(booking.get("id")).where(cb.equal(booking.get("trajet").get("id"),root.get("id")),
    cb.equal(booking.get("client").get("id"),client.getId()));
   var visibility=cb.or(cb.equal(root.get("type"),TypeTrajet.PUBLIC),cb.exists(sub));
   var zones=root.get("zone").get("id").in(allowed);
   return zoneId==null?cb.and(zones,visibility):cb.and(zones,visibility,cb.equal(root.get("zone").get("id"),zoneId));
  };
  return ReferencePageResponse.from(trips.findAll(scope,PageRequest.of(page.page(),page.size(),Sort.by("id"))).map(mapper::trajet));
 }
 public TrajetResponse detail(Authentication actor,long id){
  var client=authorization.require(actor);
  var trip=trips.findById(id).orElseThrow(ResourceNotFoundException::new);
  if(!zoneAccess.allowedZones(client.getId()).contains(trip.getZone().getId())
    ||(trip.getType()==TypeTrajet.PRIVE&&!bookings.existsByTrajetIdAndClientId(id,client.getId())))
   throw new ResourceNotFoundException();
  return mapper.trajet(trip);
 }
}
