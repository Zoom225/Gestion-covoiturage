package com.plateforme.covoiturage.dto.response;

import com.plateforme.covoiturage.entity.enums.CategorieClient;
import com.plateforme.covoiturage.entity.enums.PerimetreAdmin;
import com.plateforme.covoiturage.entity.enums.RoleTechnique;

public record ProfileResponse(Long compteId, String identifiant, RoleTechnique role,
                              Long clientId, CategorieClient categorie, Long zoneClientId,
                              PerimetreAdmin perimetreAdmin, Long zoneAdminId) {
}
