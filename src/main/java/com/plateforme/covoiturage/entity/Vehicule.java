package com.plateforme.covoiturage.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "vehicule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vehicule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @Column(nullable = false)
    private boolean actif = true;

    public void setActif(boolean actif) { this.actif = actif; }

    public Vehicule(Zone zone) {
        this.zone = zone;
    }
}
