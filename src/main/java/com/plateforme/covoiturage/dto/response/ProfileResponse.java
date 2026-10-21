package com.plateforme.covoiturage.dto.response;

import com.plateforme.covoiturage.enums.CategorieClient;
import com.plateforme.covoiturage.enums.PerimetreAdmin;
import com.plateforme.covoiturage.enums.RoleTechnique;

public record ProfileResponse(Long compteId, String identifiant, RoleTechnique role,
                              Long clientId, CategorieClient categorie, Long zoneClientId,
                              PerimetreAdmin perimetreAdmin, Long zoneAdminId) {
}
