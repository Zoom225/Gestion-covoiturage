package com.plateforme.covoiturage.service;
import com.plateforme.covoiturage.dto.request.ReferencePageRequest;
import com.plateforme.covoiturage.dto.response.*;
import com.plateforme.covoiturage.entity.*;
import com.plateforme.covoiturage.enums.TypeTrajet;
import com.plateforme.covoiturage.exception.*;
import com.plateforme.covoiturage.mapper.TrajetMapper;
import com.plateforme.covoiturage.repository.ReservationRepository;
import com.plateforme.covoiturage.security.ClientAuthorization;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ReservationService {
 private final ClientAuthorization authorization;
 private final JourneyLocks locks;
 private final BookingPolicy policy;
 private final AvailabilityService availability;
 private final ReservationRepository bookings;
 private final TrajetMapper mapper;
 @Transactional
 public ReservationResponse reserve(Authentication actor,long tripId){
  var requester=authorization.require(actor);var trip=locks.trip(tripId);
  if(trip.getType()!=TypeTrajet.PUBLIC)throw new AccessDeniedException("Acces interdit");
  return pending(trip,locks.client(requester.getId()));
 }
 @Transactional
 public ReservationResponse addPrivate(Authentication actor,long tripId,long clientId){
  var requester=authorization.require(actor);var trip=locks.trip(tripId);
  if(trip.getType()!=TypeTrajet.PRIVE||!trip.getOrganisateur().getId().equals(requester.getId()))
   throw new AccessDeniedException("Acces interdit");
  // RM37/RM44 concern the client being added; ownership is checked above.
  // Do not infer an additional restriction on managing an existing private trip.
  return pending(trip,locks.client(clientId));
 }
 private ReservationResponse pending(Trajet trip,Client client){
  policy.open(trip);policy.eligible(client,trip.getZone(),trip.getDepart());
  availability.requireAvailable(trip.getVehicule(),trip.getDepart(),trip.getArrivee());
  if(bookings.existsByTrajetIdAndClientId(trip.getId(),client.getId()))
   throw new BusinessRuleException("DUPLICATE_RESERVATION");
  return mapper.reservation(bookings.saveAndFlush(new Reservation(trip,client)));
 }
 public ReferencePageResponse<ReservationResponse> mine(Authentication actor,ReferencePageRequest page){
  var client=authorization.require(actor);
  return ReferencePageResponse.from(bookings.findByClientId(client.getId(),
   PageRequest.of(page.page(),page.size(),Sort.by("id"))).map(mapper::reservation));
 }
}
