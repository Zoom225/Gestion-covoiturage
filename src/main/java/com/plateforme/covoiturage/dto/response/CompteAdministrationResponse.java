package com.plateforme.covoiturage.dto.response;

import com.plateforme.covoiturage.enums.CategorieClient;
import com.plateforme.covoiturage.enums.EtatIdentifiants;
import com.plateforme.covoiturage.enums.PerimetreAdmin;
import com.plateforme.covoiturage.enums.RoleTechnique;

public record CompteAdministrationResponse(
        Long compteId, String identifiant, RoleTechnique role,
        Long clientId, String numeroClient, CategorieClient categorie, Long zoneClientId,
        PerimetreAdmin perimetreAdmin, Long zoneAdminId,
        boolean compteActif, Boolean clientActif, EtatIdentifiants etatIdentifiants, boolean accesEffectif) {
}
