package com.plateforme.covoiturage.service;

import com.plateforme.covoiturage.dto.request.CreateTrajetRequest;
import com.plateforme.covoiturage.dto.request.ReferencePageRequest;
import com.plateforme.covoiturage.dto.request.UpdateTrajetRequest;
import com.plateforme.covoiturage.dto.response.ReferencePageResponse;
import com.plateforme.covoiturage.dto.response.TrajetResponse;
import org.springframework.security.core.Authentication;

/** Contrat applicatif ; implementation dans service.impl. */
public interface TrajetService {
    TrajetResponse update(Authentication actor,long id,UpdateTrajetRequest request);

    int terminerTrajetsArrives();

    TrajetResponse create(Authentication actor,CreateTrajetRequest request);

    ReferencePageResponse<TrajetResponse> list(Authentication actor,ReferencePageRequest page,Long zoneId);

    TrajetResponse detail(Authentication actor,long id);
}
