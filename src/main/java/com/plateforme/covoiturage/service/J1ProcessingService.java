package com.plateforme.covoiturage.service;
import com.plateforme.covoiturage.entity.DetteTrajet;
import com.plateforme.covoiturage.enums.*;
import com.plateforme.covoiturage.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.PageRequest;
import java.time.*;
@Service @lombok.extern.slf4j.Slf4j @RequiredArgsConstructor
public class J1ProcessingService {
 private static final ZoneId BUSINESS_ZONE=ZoneId.of("Europe/Brussels");
 private final TrajetRepository trips;
 private final ReservationRepository reservations;
 private final DetteTrajetRepository debts;
 private final JourneyLocks locks;
 private final TransactionTemplate transactions;
 private final Clock clock;
 private final SensitiveOperationJournal journal;
 /**
  * Convertit à J-1 les trajets privés PLANIFIE incomplets et crée une seule dette de 80 EUR pour chaque organisateur (D5, RM41–45).
  * La borne incluse est 00:15 Europe/Brussels la veille du départ ; chaque candidat est relu sous verrou et validé dans sa propre transaction.
  * Un candidat en échec reste à reprendre lors du rattrapage, sans validation partielle.
  * @return nombre de trajets effectivement convertis pendant ce passage
  */
 public int catchUp(){
  int processed=0;
  long after=0;
  Instant now=clock.instant();
  // SQL broad filter; precise 00:15 eligibility is rechecked after locking.
  Instant until=now.atZone(BUSINESS_ZONE).toLocalDate().plusDays(2).atStartOfDay(BUSINESS_ZONE).toInstant();
  while(true){
   var candidates=trips.j1Candidates(after,until,PageRequest.of(0,100));
   if(candidates.isEmpty())return processed;
   for(Long id:candidates){
    after=id;
    try {
    Boolean changed=transactions.execute(status->{
     var trip=locks.trip(id);
     locks.client(trip.getOrganisateur().getId());
     Instant deadline=trip.getDepart().atZone(BUSINESS_ZONE).toLocalDate().minusDays(1)
      .atTime(0,15).atZone(BUSINESS_ZONE).toInstant();
     if(trip.getType()!=TypeTrajet.PRIVE||trip.getStatut()!=StatutTrajet.PLANIFIE
       ||now.isBefore(deadline)||reservations.countByTrajetIdAndStatut(id,StatutReservation.CONFIRMEE)>=BookingPolicy.MAX_CONFIRMED)return false;
     trip.processJ1(now);
     journal.record(SensitiveOperationJournal.Action.TRAJET_CONVERTI_PUBLIC,null,SensitiveOperationJournal.Resource.TRAJET,id);
     if(!debts.existsByTrajetId(id)){
      var debt=debts.saveAndFlush(new DetteTrajet(trip));
      journal.record(SensitiveOperationJournal.Action.DETTE_CREEE,null,SensitiveOperationJournal.Resource.DETTE,debt.getId());
     }
     return true;
    });
    if(Boolean.TRUE.equals(changed))processed++;
    } catch (RuntimeException failure) {
     log.warn("J1: traitement du trajet {} a reprendre",id);
    }
   }
  }
 }
}
