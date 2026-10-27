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
 /**
  * Vérifie les zones autorisées, l'absence de dette ACTIVE et l'ouverture J-30, J-10 ou J-3 selon la catégorie (RM05–08, RM36–38).
  */
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
  * Empêche de préparer l'annulation privée incomplète avant J-1 qui éviterait la dette de 80 EUR (décision de #26, PR #44).
  * Le futur appelant doit authentifier l'organisateur, verrouiller et relire le trajet dans la transaction de mutation.
  * Un retour normal ne donne aucun droit d'annulation ; aucun parcours actuel n'appelle cette garde.
  * @param trip trajet persistant relu sous JourneyLocks
  * @throws com.plateforme.covoiturage.exception.BusinessRuleException si l'annulation contournerait la dette prévue à J-1
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
 /**
  * Vérifie que le trajet est PLANIFIE et compte moins de quatre réservations CONFIRMEE, sans garantir une place EN_ATTENTE (D4).
  * @return nombre actuel de participants confirmés
  * @throws com.plateforme.covoiturage.exception.BusinessRuleException si le trajet est fermé ou complet
  */
 public long open(Trajet trip){
  if(trip.getStatut()!=StatutTrajet.PLANIFIE)throw new BusinessRuleException("TRIP_NOT_OPEN");
  long confirmed=reservations.countByTrajetIdAndStatut(trip.getId(),StatutReservation.CONFIRMEE);
  if(confirmed>=MAX_CONFIRMED)throw new BusinessRuleException("TRIP_FULL");
  return confirmed;
 }
}
