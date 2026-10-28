package com.plateforme.covoiturage.mapper;
import com.plateforme.covoiturage.dto.response.*;
import com.plateforme.covoiturage.entity.*;
import org.springframework.stereotype.Component;
@Component
public class TrajetMapper {
 public TrajetResponse trajet(Trajet t){
  return new TrajetResponse(t.getId(),t.getZone().getId(),t.getVehicule().getId(),t.getOrganisateur().getId(),
   t.getDepart(),t.getArrivee(),t.getZone().getFuseauHoraire(),t.getType(),t.getStatut(),t.getOrigine(),t.getDestination(),
   t.getTraiteJ1Le(),t.isVerificationJ1Requise());
 }
 public ReservationResponse reservation(Reservation r){
  return new ReservationResponse(r.getId(),r.getTrajet().getId(),r.getClient().getId(),r.getStatut(),
   r.getTrajet().getOrganisateur().getId().equals(r.getClient().getId()),r.getAnnuleLe());
 }
}
