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
 private static String location(String value){
  if(value==null||value.isBlank()||value.strip().length()>255)
   throw new IllegalArgumentException("Un lieu geographique non vide de 255 caracteres maximum est requis");
  return value.strip();
 }
 @Column(name="traite_j1_le") private Instant traiteJ1Le;
 public void becomePublic(){type=TypeTrajet.PUBLIC;}
 public void processJ1(Instant now){becomePublic();traiteJ1Le=now;}
 public void markFull(){statut=StatutTrajet.COMPLET;}
}
