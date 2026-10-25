package com.plateforme.covoiturage.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "zone")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Zone {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "text")
    private String nom;

    @Column(nullable=false)
    private boolean actif=true;
    @Column(name="fuseau_horaire",length=80)
    private String fuseauHoraire;
    @Column(name="ouverte_occasionnels",nullable=false)
    private boolean ouverteOccasionnels;
    public void configure(String nom,String timezone,boolean occasionnels){
        this.nom=nom;this.fuseauHoraire=timezone;this.ouverteOccasionnels=occasionnels;
    }
    public void setActif(boolean actif){this.actif=actif;}

    public Zone(String nom) {
        this.nom = nom;
    }
}
