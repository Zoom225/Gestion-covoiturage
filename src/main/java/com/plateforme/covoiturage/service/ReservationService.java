package com.plateforme.covoiturage.service;
import com.plateforme.covoiturage.dto.request.ReferencePageRequest;
import com.plateforme.covoiturage.dto.response.*;
import com.plateforme.covoiturage.entity.*;
import com.plateforme.covoiturage.enums.TypeTrajet;
import com.plateforme.covoiturage.enums.StatutReservation;
import com.plateforme.covoiturage.exception.*;
import com.plateforme.covoiturage.mapper.TrajetMapper;
import com.plateforme.covoiturage.repository.ReservationRepository;
import com.plateforme.covoiturage.security.ClientAuthorization;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ReservationService {
 private final ClientAuthorization authorization;
 private final JourneyLocks locks;
 private final BookingPolicy policy;
 private final AvailabilityService availability;
 private final ReservationRepository bookings;
 private final TrajetMapper mapper;
 private final EntityManager entityManager;
 private final Clock clock;
 private final SensitiveOperationJournal journal;
 private final PaymentService payments;
 /**
  * Crée la demande personnelle EN_ATTENTE d'un trajet public, sous les verrous partagés, sans paiement ni confirmation implicite.
  */
 @Transactional
 public ReservationResponse reserve(Authentication actor,long tripId){
  var requester=authorization.require(actor);var trip=locks.trip(tripId);
  if(trip.getType()!=TypeTrajet.PUBLIC)throw new AccessDeniedException("Acces interdit");
  return pending(trip,locks.client(requester.getId()));
 }
 /**
  * Permet au seul organisateur d'ajouter un client éligible à son trajet privé, sans lui attribuer une confirmation gratuite (RM18, D4).
  */
 @Transactional
 public ReservationResponse addPrivate(Authentication actor,long tripId,long clientId){
  var requester=authorization.require(actor);var trip=locks.trip(tripId);
  if(trip.getType()!=TypeTrajet.PRIVE||!trip.getOrganisateur().getId().equals(requester.getId()))
   throw new AccessDeniedException("Acces interdit");
  // RM37/RM44 concern the client being added; ownership is checked above.
  // Do not infer an additional restriction on managing an existing private trip.
  return pending(trip,locks.client(clientId));
 }
 /**
  * Recontrôle l'ouverture, l'éligibilité, la disponibilité et l'absence de réservation active avant de conserver une nouvelle ligne EN_ATTENTE (V16).
  */
 private ReservationResponse pending(Trajet trip,Client client){
  policy.open(trip);policy.eligible(client,trip.getZone(),trip.getDepart());
  availability.requireAvailable(trip.getVehicule(),trip.getDepart(),trip.getArrivee());
  if(bookings.existsByTrajetIdAndClientIdAndStatutNot(trip.getId(),client.getId(),StatutReservation.ANNULEE))
   throw new BusinessRuleException("DUPLICATE_RESERVATION");
  return mapper.reservation(bookings.saveAndFlush(new Reservation(trip,client)));
 }
 /**
  * Annule uniquement sa propre réservation avant le départ, de façon idempotente avant cette limite, sans effacer paiement ni dette (PR #43, V17).
  * Le trajet et le client sont verrouillés avant la relecture ; le remboursement éligible partage la transaction.
  * @param actor client authentifié propriétaire de la réservation
  * @param reservationId identifiant de la réservation à conserver dans l'historique
  * @return réservation conservée au statut ANNULEE
  * @throws com.plateforme.covoiturage.exception.ResourceNotFoundException si la réservation est absente ou appartient à un autre client
  * @throws com.plateforme.covoiturage.exception.BusinessRuleException si l'instant de départ est atteint
  */
 @Transactional
 public ReservationResponse cancel(Authentication actor,long reservationId){
  var requester=authorization.require(actor);
  var booking=bookings.findById(reservationId).orElseThrow(ResourceNotFoundException::new);
  if(!booking.getClient().getId().equals(requester.getId()))throw new ResourceNotFoundException();
  var trip=locks.trip(booking.getTrajet().getId());
  locks.client(requester.getId());
  entityManager.refresh(booking);
  if(!booking.getClient().getId().equals(requester.getId())
    ||!booking.getTrajet().getId().equals(trip.getId()))throw new ResourceNotFoundException();
  var cancelledAt=clock.instant();
  if(!cancelledAt.isBefore(trip.getDepart()))throw new BusinessRuleException("DEPARTURE_PASSED");
  if(booking.getStatut()!=StatutReservation.ANNULEE){
   boolean confirmed=booking.getStatut()==StatutReservation.CONFIRMEE;
   booking.cancel(cancelledAt);bookings.flush();
   if(confirmed)payments.refundSeat(actor,booking,cancelledAt);
   journal.record(SensitiveOperationJournal.Action.RESERVATION_ANNULEE,actor,
    SensitiveOperationJournal.Resource.RESERVATION,reservationId);
  }
  return mapper.reservation(booking);
 }
 /**
  * Consulte uniquement les réservations du client authentifié, y compris les lignes historiques annulées.
  */
 public ReferencePageResponse<ReservationResponse> mine(Authentication actor,ReferencePageRequest page){
  var client=authorization.require(actor);
  return ReferencePageResponse.from(bookings.findByClientId(client.getId(),
   PageRequest.of(page.page(),page.size(),Sort.by("id"))).map(mapper::reservation));
 }
}
