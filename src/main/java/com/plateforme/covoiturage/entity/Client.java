package com.plateforme.covoiturage.entity;

import com.plateforme.covoiturage.enums.CategorieClient;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "client")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, columnDefinition = "text")
    private String numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategorieClient categorie;

    @Column(nullable = false)
    private boolean actif;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    public Client(String numero, CategorieClient categorie, boolean actif, Zone zone) {
        this.numero = numero;
        this.categorie = categorie;
        this.actif = actif;
        this.zone = zone;
    }
}
