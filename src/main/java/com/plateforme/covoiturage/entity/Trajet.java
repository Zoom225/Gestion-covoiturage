package com.plateforme.covoiturage.entity;
import com.plateforme.covoiturage.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Trajet {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="zone_id") private Zone zone;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="vehicule_id") private Vehicule vehicule;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="organisateur_id") private Client organisateur;
 @Column(nullable=false) private Instant depart;
 @Column(nullable=false) private Instant arrivee;
 @Column(length=255) private String origine;
 @Column(length=255) private String destination;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private TypeTrajet type;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=12) private StatutTrajet statut=StatutTrajet.PLANIFIE;
 public Trajet(Zone zone,Vehicule vehicule,Client organisateur,Instant depart,Instant arrivee,TypeTrajet type,
               String origine,String destination){
  this.origine=location(origine);this.destination=location(destination);
  this.zone=zone;this.vehicule=vehicule;this.organisateur=organisateur;
  this.depart=depart;this.arrivee=arrivee;this.type=type;
 }
 /**
  * Exige un lieu non vide de 255 caractères maximum et retire les espaces périphériques sans inventer de contrainte géographique.
  */
 private static String location(String value){
  if(value==null||value.isBlank()||value.strip().length()>255)
   throw new IllegalArgumentException("Un lieu geographique non vide de 255 caracteres maximum est requis");
  return value.strip();
 }
 /**
  * Change uniquement le type en PUBLIC sans annuler les réservations ni les preuves de paiement.
  */
 @Column(name="traite_j1_le") private Instant traiteJ1Le;
 @Column(name="verification_j1_requise",nullable=false) private boolean verificationJ1Requise;
 public void becomePublic(){type=TypeTrajet.PUBLIC;}
 /** Appelé par le service après tous les contrôles, sous les verrous partagés. */
 public void update(Vehicule vehicle,Instant start,Instant end,String origin,String arrival){
  origine=location(origin);destination=location(arrival);
  vehicule=vehicle;depart=start;arrivee=end;
 }
 /**
  * Enregistre une conversion décidée par le contrôle J-1, sans modifier les dettes existantes.
  */
 public void processJ1(Instant now){becomePublic();recordJ1Check(now);}
 /** Mémorise aussi un contrôle réussi : une annulation ultérieure ne relance pas J-1. */
 public void recordJ1Check(Instant now){if(traiteJ1Le==null)traiteJ1Le=now;verificationJ1Requise=false;}
 public void requireJ1Verification(){verificationJ1Requise=true;}
 /**
  * Marque le trajet COMPLET après la quatrième confirmation validée par le service de paiement (D4).
  */
 public void markFull(){statut=StatutTrajet.COMPLET;}
 /** Le service vérifie l'arrivée et les confirmations sous verrou avant cette transition. */
 public void markCompleted(){statut=StatutTrajet.REALISE;}
}
