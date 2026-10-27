package com.plateforme.covoiturage.service;
import com.plateforme.covoiturage.entity.*;
import com.plateforme.covoiturage.exception.BusinessRuleException;
import com.plateforme.covoiturage.repository.ZoneAccessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
/** D3 shared by trip creation, booking and payment. Managed database entities only. */
@Component @RequiredArgsConstructor
public class ZoneAccessPolicy {
 private final ZoneAccessRepository grants;
 /**
  * Vérifie l'activité et les droits de zone : LOCAL dans sa zone, VIP sur autorisation explicite et OCCASIONNEL dans une zone ouverte (D3).
  */
 public boolean permits(Client client,Zone zone) {
  return client.isActif() && zone.isActif() && switch(client.getCategorie()) {
   case LOCAL -> client.getZone()!=null && client.getZone().getId().equals(zone.getId());
   case VIP -> grants.exists(client.getId(),zone.getId());
   case OCCASIONNEL -> zone.isOuverteOccasionnels();
  };
 }
 /**
  * Refuse une zone non autorisée ou dépourvue de fuseau explicite, sans élargir les droits de réservation (D2–D3).
  */
 public void require(Client client,Zone zone){
  if(!permits(client,zone))throw new org.springframework.security.access.AccessDeniedException("Acces interdit");
  if(zone.getFuseauHoraire()==null)throw new BusinessRuleException("ZONE_TIMEZONE_REQUIRED");
 }
}
