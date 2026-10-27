package com.plateforme.covoiturage.entity;
import com.plateforme.covoiturage.enums.StatutReservation;
import jakarta.persistence.*;
import lombok.*;
@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Reservation {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="trajet_id") private Trajet trajet;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="client_id") private Client client;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=12) private StatutReservation statut=StatutReservation.EN_ATTENTE;
 public Reservation(Trajet trajet,Client client){this.trajet=trajet;this.client=client;}
 /**
  * Marque la réservation CONFIRMEE uniquement dans le service de paiement transactionnel, dont la preuve de 20 EUR est exigée par PostgreSQL.
  */
 public void confirm(){statut=StatutReservation.CONFIRMEE;}
 /**
  * Marque la réservation ANNULEE sans supprimer sa ligne ni son éventuel paiement historique (V17).
  */
 public void cancel(){statut=StatutReservation.ANNULEE;}
}
