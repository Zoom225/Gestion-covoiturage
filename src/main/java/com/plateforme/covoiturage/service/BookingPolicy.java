package com.plateforme.covoiturage.service;
import com.plateforme.covoiturage.entity.*;
import com.plateforme.covoiturage.enums.*;
import com.plateforme.covoiturage.exception.BusinessRuleException;
import com.plateforme.covoiturage.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
@Component @RequiredArgsConstructor
public class BookingPolicy {
 private final ZoneAccessPolicy zones;
 private final DetteTrajetRepository debts;
 private final ReservationRepository reservations;
 private final Clock clock;
 public void eligible(Client client,Zone zone,Instant departure){
  zones.require(client,zone);
  if(debts.existsByClientIdAndStatut(client.getId(),"ACTIVE"))throw new BusinessRuleException("ACTIVE_DEBT");
  int days=switch(client.getCategorie()){case VIP->30;case LOCAL->10;case OCCASIONNEL->3;};
  Instant now=clock.instant();
  Instant opens=departure.atZone(ZoneId.of(zone.getFuseauHoraire())).minusDays(days).toInstant();
  if(!departure.isAfter(now)||now.isBefore(opens))throw new BusinessRuleException("BOOKING_HORIZON");
 }
 public static final int MAX_CONFIRMED=4;
 /**
  * Preparatory guard only: returning does not authorize a trip cancellation.
  * A future cancellation service must authenticate the organizer, take JourneyLocks
  * and refresh the trip before calling this guard in the same transaction.
  * Reservation cancellation and the automatic J-1 processing do not call it.
  */
 public void requireCancellationPreservesJ1Debt(Trajet trip){
  if(trip.getType()!=TypeTrajet.PRIVE||trip.getStatut()!=StatutTrajet.PLANIFIE)return;
  // Same calendar boundary as J1ProcessingService; never departure minus 24 hours.
  Instant deadline=trip.getDepart().atZone(ZoneId.of("Europe/Brussels")).toLocalDate().minusDays(1)
   .atTime(0,15).atZone(ZoneId.of("Europe/Brussels")).toInstant();
  if(clock.instant().isBefore(deadline)
    &&reservations.countByTrajetIdAndStatut(trip.getId(),StatutReservation.CONFIRMEE)<MAX_CONFIRMED
    &&!debts.existsByTrajetId(trip.getId()))
   throw new BusinessRuleException("PRIVATE_CANCELLATION_AVOIDS_J1_DEBT");
 }
 public long open(Trajet trip){
  if(trip.getStatut()!=StatutTrajet.PLANIFIE)throw new BusinessRuleException("TRIP_NOT_OPEN");
  long confirmed=reservations.countByTrajetIdAndStatut(trip.getId(),StatutReservation.CONFIRMEE);
  if(confirmed>=MAX_CONFIRMED)throw new BusinessRuleException("TRIP_FULL");
  return confirmed;
 }
}
