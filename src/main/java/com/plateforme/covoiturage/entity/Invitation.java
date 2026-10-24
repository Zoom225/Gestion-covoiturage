package com.plateforme.covoiturage.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "invitation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invitation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compte_id", nullable = false)
    private Compte compte;
    @Column(nullable = false, unique = true, length = 64)
    private String empreinte;
    @Column(name = "cree_a", nullable = false)
    private Instant creeA;
    @Column(name = "expire_a", nullable = false)
    private Instant expireA;
    @Column(name = "consommee_a")
    private Instant consommeeA;
    @Column(name = "revoquee_a")
    private Instant revoqueeA;

    public Invitation(Compte compte, String empreinte, Instant creeA, Instant expireA) {
        this.compte = compte;
        this.empreinte = empreinte;
        this.creeA = creeA;
        this.expireA = expireA;
    }

}
