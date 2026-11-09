package com.plateforme.covoiturage.service;

import com.plateforme.covoiturage.dto.response.TokenResponse;

/** Contrat applicatif ; implementation dans service.impl. */
public interface TokenService {
    TokenResponse issue(Long compteId);
}
