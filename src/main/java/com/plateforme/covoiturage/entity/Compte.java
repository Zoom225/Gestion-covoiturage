package com.plateforme.covoiturage.entity;

import com.plateforme.covoiturage.enums.PerimetreAdmin;
import com.plateforme.covoiturage.enums.RoleTechnique;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "compte")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Compte {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String identifiant;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(nullable = false)
    private boolean actif;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RoleTechnique role;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", unique = true)
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(name = "perimetre_admin", length = 10)
    private PerimetreAdmin perimetreAdmin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_admin_id")
    private Zone zoneAdmin;

    /** État d'accès commun à l'authentification et à la consultation administrative. */
    public boolean isAccesEffectif() {
        return actif && (client == null || client.isActif());
    }

    public Compte(String identifiant, String passwordHash, boolean actif, RoleTechnique role,
                  Client client, PerimetreAdmin perimetreAdmin, Zone zoneAdmin) {
        this.identifiant = identifiant;
        this.passwordHash = passwordHash;
        this.actif = actif;
        this.role = role;
        this.client = client;
        this.perimetreAdmin = perimetreAdmin;
        this.zoneAdmin = zoneAdmin;
    }
}
