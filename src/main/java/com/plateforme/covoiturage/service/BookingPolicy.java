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
 public void open(Trajet trip){
  if(trip.getStatut()!=StatutTrajet.PLANIFIE)throw new BusinessRuleException("TRIP_NOT_OPEN");
  if(reservations.countByTrajetIdAndStatut(trip.getId(),StatutReservation.CONFIRMEE)>=4)
   throw new BusinessRuleException("TRIP_FULL");
 }
}
