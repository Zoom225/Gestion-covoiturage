package com.plateforme.covoiturage.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Référence de lieux, sans organisateur, zone d'exploitation, véhicule ou date. */
@Entity
@Table(name="itineraire_propose")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ItinerairePropose {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false,length=255)
    private String origine;
    @Column(nullable=false,length=255)
    private String destination;
}
