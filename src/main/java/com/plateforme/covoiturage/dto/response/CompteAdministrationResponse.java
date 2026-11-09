package com.plateforme.covoiturage.dto.response;

import com.plateforme.covoiturage.entity.enums.CategorieClient;
import com.plateforme.covoiturage.entity.enums.EtatIdentifiants;
import com.plateforme.covoiturage.entity.enums.PerimetreAdmin;
import com.plateforme.covoiturage.entity.enums.RoleTechnique;

public record CompteAdministrationResponse(
        Long compteId, String identifiant, RoleTechnique role,
        Long clientId, String numeroClient, CategorieClient categorie, Long zoneClientId,
        PerimetreAdmin perimetreAdmin, Long zoneAdminId,
        boolean compteActif, Boolean clientActif, EtatIdentifiants etatIdentifiants, boolean accesEffectif) {
}
