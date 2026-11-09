package com.plateforme.covoiturage.service;

import com.plateforme.covoiturage.dto.request.LoginRequest;
import com.plateforme.covoiturage.dto.response.ProfileResponse;
import com.plateforme.covoiturage.dto.response.TokenResponse;
import com.plateforme.covoiturage.security.ComptePrincipal;

/** Contrat applicatif ; implementation dans service.impl. */
public interface AuthService {
    TokenResponse login(LoginRequest request);

    ComptePrincipal resolvePrincipal(String subject);

    ProfileResponse me(ComptePrincipal principal);

    void accepterInvitation(String jeton, String password);
}
