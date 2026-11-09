package com.plateforme.covoiturage.service;

import com.plateforme.covoiturage.dto.request.CompteSearchRequest;
import com.plateforme.covoiturage.dto.request.CreateAdministrateurRequest;
import com.plateforme.covoiturage.dto.request.CreateClientRequest;
import com.plateforme.covoiturage.dto.response.CompteAdministrationResponse;
import com.plateforme.covoiturage.dto.response.CompteInvitationResponse;
import com.plateforme.covoiturage.dto.response.ReferencePageResponse;
import com.plateforme.covoiturage.dto.response.InvitationStatusResponse;
import org.springframework.security.core.Authentication;

/** Contrat applicatif ; implementation dans service.impl. */
public interface CompteService {
    CompteInvitationResponse createClient(Authentication actor, CreateClientRequest request);

    CompteInvitationResponse createAdministrateur(Authentication actor, CreateAdministrateurRequest request);

    void bootstrapGlobal(String email);

    InvitationStatusResponse renewInvitation(Authentication actor, long id);

    ReferencePageResponse<CompteAdministrationResponse> search(Authentication actor, CompteSearchRequest search);

    CompteAdministrationResponse findById(Authentication actor, long id);
}
