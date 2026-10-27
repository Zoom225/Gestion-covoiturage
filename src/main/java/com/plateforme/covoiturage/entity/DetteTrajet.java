package com.plateforme.covoiturage.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
@Entity @Table(name="dette_trajet") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class DetteTrajet {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="trajet_id",unique=true) private Trajet trajet;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="client_id") private Client client;
 @Column(nullable=false,precision=10,scale=2) private BigDecimal montant=new BigDecimal("80.00");
 @Column(nullable=false,precision=10,scale=2) private BigDecimal regle=BigDecimal.ZERO;
 @Column(nullable=false,length=12) private String statut="ACTIVE";
 public DetteTrajet(Trajet trajet){this.trajet=trajet;this.client=trajet.getOrganisateur();}
 /**
  * Ajoute un montant positif sans dépasser la dette et passe à REGULARISEE uniquement lorsque le total est intégralement réglé (RM52–54).
  */
 public void settle(BigDecimal amount){
  if(amount==null||amount.signum()<=0||regle.add(amount).compareTo(montant)>0)
   throw new com.plateforme.covoiturage.exception.BusinessRuleException("INVALID_DEBT_AMOUNT");
  regle=regle.add(amount);
  if(regle.compareTo(montant)==0)statut="REGULARISEE";
 }
}
