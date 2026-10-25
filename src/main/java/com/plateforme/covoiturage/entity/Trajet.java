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
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private TypeTrajet type;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=12) private StatutTrajet statut=StatutTrajet.PLANIFIE;
 public Trajet(Zone zone,Vehicule vehicule,Client organisateur,Instant depart,Instant arrivee,TypeTrajet type){
  this.zone=zone;this.vehicule=vehicule;this.organisateur=organisateur;
  this.depart=depart;this.arrivee=arrivee;this.type=type;
 }
 public void becomePublic(){type=TypeTrajet.PUBLIC;}
 public void markFull(){statut=StatutTrajet.COMPLET;}
}
