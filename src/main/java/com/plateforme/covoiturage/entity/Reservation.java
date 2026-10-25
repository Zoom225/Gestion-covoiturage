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
 /** Called exclusively by the transactional payment service. */
 public void confirm(){statut=StatutReservation.CONFIRMEE;}
}
