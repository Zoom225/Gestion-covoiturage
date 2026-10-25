package com.plateforme.covoiturage.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="paiement_place") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class PaiementPlace {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="reservation_id") private Reservation reservation;
 @Column(name="cle_idempotence",nullable=false,unique=true) private UUID cleIdempotence;
 @Column(nullable=false,precision=10,scale=2) private BigDecimal montant;
 @Column(nullable=false,length=20) private String mode;
 @Column(name="valide_le",nullable=false) private Instant valideLe;
 public PaiementPlace(Reservation target,UUID key,BigDecimal amount,String mode,Instant now){
  this.reservation=target;this.cleIdempotence=key;this.montant=amount;this.mode=mode;this.valideLe=now;
 }
}
