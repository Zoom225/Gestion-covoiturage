package com.plateforme.covoiturage.entity;

import com.plateforme.covoiturage.exception.BusinessRuleException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dette_trajet")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DetteTrajet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trajet_id", unique = true)
    private Trajet trajet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id")
    private Client client;

    /** La dette J-1 est fixée à 80 EUR pour l'organisateur d'un trajet privé incomplet. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal montant = new BigDecimal("80.00");

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal regle = BigDecimal.ZERO;

    @Column(nullable = false, length = 12)
    private String statut = "ACTIVE";

    public DetteTrajet(Trajet trajet) {
        this.trajet = trajet;
        this.client = trajet.getOrganisateur();
    }

    /** RM52–54 : la dette reste ACTIVE jusqu'à son règlement intégral. */
    public void settle(BigDecimal amount) {
        requireValidPaymentAmount(amount);
        regle = regle.add(amount);
        if (regle.compareTo(montant) == 0) {
            statut = "REGULARISEE";
        }
    }

    /** Source unique du contrôle métier : montant positif, au centime et limité au solde. */
    public void requireValidPaymentAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2
                || regle.add(amount).compareTo(montant) > 0) {
            throw new BusinessRuleException("INVALID_DEBT_AMOUNT");
        }
    }
}
