package com.plateforme.covoiturage.security;
import com.plateforme.covoiturage.entity.Client;
import com.plateforme.covoiturage.enums.RoleTechnique;
import com.plateforme.covoiturage.repository.CompteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class ClientAuthorization {
 private final CompteRepository accounts;
 /**
  * Exige un principal CLIENT authentifié puis relit son compte pour refuser un accès devenu inactif avant une opération métier.
  */
 public Client require(Authentication actor){
  if(actor==null||!actor.isAuthenticated()||!(actor.getPrincipal() instanceof ComptePrincipal principal))
   throw new AccessDeniedException("Acces interdit");
  var account=accounts.findById(principal.id()).orElseThrow(()->new AccessDeniedException("Acces interdit"));
  if(account.getRole()!=RoleTechnique.CLIENT||!account.isAccesEffectif())
   throw new AccessDeniedException("Acces interdit");
  return account.getClient();
 }
}
