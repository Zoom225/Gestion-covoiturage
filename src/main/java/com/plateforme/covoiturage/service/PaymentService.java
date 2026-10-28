package com.plateforme.covoiturage.service;
import com.plateforme.covoiturage.entity.*;
import com.plateforme.covoiturage.enums.*;
import com.plateforme.covoiturage.dto.request.ReferencePageRequest;
import com.plateforme.covoiturage.dto.response.*;
import com.plateforme.covoiturage.repository.*;
import com.plateforme.covoiturage.security.ClientAuthorization;
import com.plateforme.covoiturage.mapper.PaymentMapper;
import com.plateforme.covoiturage.payment.PaymentGateway;
import com.plateforme.covoiturage.exception.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class PaymentService {
 private static final BigDecimal SEAT_PRICE=new BigDecimal("20.00");
 private final ClientAuthorization authorization;
 private final JourneyLocks locks;
 private final BookingPolicy policy;
 private final AvailabilityService availability;
 private final ReservationRepository reservations;
 private final DetteTrajetRepository debts;
 private final PaiementPlaceRepository seatPayments;
 private final PaiementDetteRepository debtPayments;
 private final PaymentGateway gateway;
 private final PaymentMapper mapper;
 private final EntityManager entityManager;
 private final Clock clock;
 private final SensitiveOperationJournal journal;

 /** Rembourse intégralement à H-24 inclus, sous les verrous et dans la transaction d'annulation. */
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
 public void refundSeat(Authentication actor,Reservation booking,Instant cancelledAt){
  if(booking.getStatut()!=StatutReservation.ANNULEE)throw new BusinessRuleException("RESERVATION_NOT_CANCELLED");
  if(cancelledAt.isAfter(booking.getTrajet().getDepart().minus(Duration.ofHours(24))))return;
  var payment=seatPayments.findByReservationId(booking.getId())
   .orElseThrow(()->new IllegalStateException("La réservation confirmée doit avoir un paiement"));
  if(payment.getRembourseLe()!=null)return;
  gateway.refund(payment.getCleIdempotence(),payment.getMontant());
  payment.refund(cancelledAt);
  journal.record(SensitiveOperationJournal.Action.PLACE_REMBOURSEE,actor,
   SensitiveOperationJournal.Resource.PAIEMENT_PLACE,payment.getId());
 }

 /**
  * Valide une seule fois les 20 EUR du propriétaire et confirme sa place sous verrou, sans dépasser quatre participants confirmés (D4, RM27, RM40).
  * La clé idempotente est recontrôlée ; la quatrième confirmation publie COMPLET dans la même transaction.
  */
 @Transactional
 public PaymentResponse paySeat(Authentication actor,long reservationId,UUID key){
  var requester=authorization.require(actor);
  var booking=reservations.findById(reservationId).orElseThrow(ResourceNotFoundException::new);
  if(!booking.getClient().getId().equals(requester.getId()))throw new ResourceNotFoundException();
  var trip=locks.trip(booking.getTrajet().getId());var client=locks.client(requester.getId());
  entityManager.refresh(booking);
  var sameKey=seatPayments.findByCleIdempotence(key).orElse(null);
  if(sameKey!=null){
   if(!sameKey.getReservation().getId().equals(reservationId))throw new BusinessRuleException("IDEMPOTENCY_CONFLICT");
   return mapper.place(sameKey);
  }
  if(seatPayments.findByReservationId(reservationId).isPresent())throw new BusinessRuleException("ALREADY_PAID");
  long confirmed=policy.open(trip);
  policy.eligible(client,trip.getZone(),trip.getDepart());
  if(booking.getStatut()!=StatutReservation.EN_ATTENTE)throw new BusinessRuleException("RESERVATION_NOT_PENDING");
  availability.requireAvailable(trip.getVehicule(),trip.getDepart(),trip.getArrivee());
  String mode=gateway.validate(key,SEAT_PRICE).name();
  var payment=seatPayments.save(new PaiementPlace(booking,key,SEAT_PRICE,mode,clock.instant()));
  booking.confirm();
  journal.record(SensitiveOperationJournal.Action.PLACE_PAYEE,actor,SensitiveOperationJournal.Resource.PAIEMENT_PLACE,payment.getId());
  journal.record(SensitiveOperationJournal.Action.RESERVATION_CONFIRMEE,actor,SensitiveOperationJournal.Resource.RESERVATION,booking.getId());
  // Confirm while PLANIFIE, then publish COMPLET under the same retained lock.
  entityManager.flush();
  if(confirmed==BookingPolicy.MAX_CONFIRMED-1){
   trip.markFull();entityManager.flush();
   journal.record(SensitiveOperationJournal.Action.TRAJET_COMPLET,actor,SensitiveOperationJournal.Resource.TRAJET,trip.getId());
  }
  // Deferred PostgreSQL proof enforces both directions at commit.
  return mapper.place(payment);
 }
 /**
  * Conserve le règlement de dette du propriétaire sans dépasser le solde, maintient ACTIVE après paiement partiel et régularise au règlement intégral (RM44, RM52–54).
  * Aucun règlement ne réactive un compte ou client désactivé.
  * @param amount montant strictement positif, avec au plus deux décimales
  * @return preuve de règlement conservée, ou preuve existante pour un rejeu idempotent
  */
 @Transactional
 public PaymentResponse payDebt(Authentication actor,long debtId,UUID key,BigDecimal amount){
  var requester=authorization.require(actor);locks.client(requester.getId());
  debts.lock(debtId).orElseThrow(ResourceNotFoundException::new);
  var debt=debts.findById(debtId).orElseThrow(ResourceNotFoundException::new);entityManager.refresh(debt);
  if(!debt.getClient().getId().equals(requester.getId()))throw new ResourceNotFoundException();
  var sameKey=debtPayments.findByCleIdempotence(key).orElse(null);
  if(sameKey!=null){
   if(!sameKey.getDette().getId().equals(debtId)||sameKey.getMontant().compareTo(amount)!=0)
    throw new BusinessRuleException("IDEMPOTENCY_CONFLICT");
   return mapper.debt(sameKey);
  }
  if(amount==null||amount.signum()<=0||amount.scale()>2||debt.getRegle().add(amount).compareTo(debt.getMontant())>0)
   throw new BusinessRuleException("INVALID_DEBT_AMOUNT");
  String mode=gateway.validate(key,amount).name();
  debt.settle(amount);
  var payment=debtPayments.save(new PaiementDette(debt,key,amount,mode,clock.instant()));
  entityManager.flush();
  journal.record(SensitiveOperationJournal.Action.DETTE_REGLEE,actor,SensitiveOperationJournal.Resource.PAIEMENT_DETTE,payment.getId());
  if("REGULARISEE".equals(debt.getStatut()))
   journal.record(SensitiveOperationJournal.Action.DETTE_REGULARISEE,actor,SensitiveOperationJournal.Resource.DETTE,debtId);
  return mapper.debt(payment);
 }
 /**
  * Retourne l'historique du propriétaire en séparant les paiements de places et de dettes, avec une lecture cohérente des totaux et de la page.
  */
 @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
 public ReferencePageResponse<PaymentResponse> history(Authentication actor,ReferencePageRequest page){
  var client=authorization.require(actor);
  return ReferencePageResponse.from(seatPayments.history(client.getId(),
   PageRequest.of(page.page(),page.size())).map(mapper::history));
 }
 /**
  * Liste uniquement les dettes du client authentifié, sans expiration ni effacement implicite.
  */
 public ReferencePageResponse<DetteResponse> mine(Authentication actor,ReferencePageRequest page){
  var client=authorization.require(actor);
  return ReferencePageResponse.from(debts.findByClientId(client.getId(),
   PageRequest.of(page.page(),page.size(),Sort.by("id"))).map(mapper::dette));
 }
}
