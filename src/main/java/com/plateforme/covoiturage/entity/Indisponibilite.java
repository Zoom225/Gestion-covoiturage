package com.plateforme.covoiturage.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

/** Période à cible fixe ; modification des dates sans transfert implicite. */
@Entity
@Table(name="indisponibilite")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Indisponibilite {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="zone_id")
    private Zone zone;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="vehicule_id")
    private Vehicule vehicule;
    @Column(nullable=false)
    private Instant debut;
    @Column(nullable=false)
    private Instant fin;

    private Indisponibilite(Zone zone, Vehicule vehicule, Instant debut, Instant fin) {
        this.zone=zone; this.vehicule=vehicule; this.debut=debut; this.fin=fin;
    }
    public static Indisponibilite pourZone(Zone zone, Instant debut, Instant fin) {
        return new Indisponibilite(java.util.Objects.requireNonNull(zone),null,debut,fin);
    }
    public static Indisponibilite pourVehicule(Vehicule vehicule, Instant debut, Instant fin) {
        return new Indisponibilite(null,java.util.Objects.requireNonNull(vehicule),debut,fin);
    }
    public void configure(Instant debut, Instant fin) { this.debut=debut; this.fin=fin; }
}
