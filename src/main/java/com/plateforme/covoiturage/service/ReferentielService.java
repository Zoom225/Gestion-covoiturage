package com.plateforme.covoiturage.service;

import com.plateforme.covoiturage.dto.request.ReferencePageRequest;
import com.plateforme.covoiturage.dto.response.AdminStatisticsResponse;
import com.plateforme.covoiturage.dto.response.IndisponibiliteResponse;
import com.plateforme.covoiturage.dto.response.ItineraireProposeResponse;
import com.plateforme.covoiturage.dto.response.ReferencePageResponse;
import com.plateforme.covoiturage.dto.response.VehiculeResponse;
import com.plateforme.covoiturage.dto.response.ZoneResponse;
import org.springframework.security.core.Authentication;

/** Contrat applicatif ; implementation dans service.impl. */
public interface ReferentielService {
    ReferencePageResponse<ItineraireProposeResponse> clientItineraires(Authentication actor,
            ReferencePageRequest page, com.plateforme.covoiturage.dto.request.ItineraireSearchRequest search);

    AdminStatisticsResponse statistics(Authentication actor,Long zoneId);

    ReferencePageResponse<ZoneResponse> clientZones(Authentication actor,ReferencePageRequest page);

    ReferencePageResponse<VehiculeResponse> clientVehicles(Authentication actor,ReferencePageRequest page,Long zoneId);

    ZoneResponse createZone(Authentication actor,com.plateforme.covoiturage.dto.request.ZoneWriteRequest request);

    ZoneResponse updateZone(Authentication actor,long id,com.plateforme.covoiturage.dto.request.ZoneWriteRequest request);

    ZoneResponse zoneStatus(Authentication actor,long id,boolean active);

    void vipAccess(Authentication actor,long zoneId,long clientId,boolean allow);

    ReferencePageResponse<ZoneResponse> zones(Authentication actor,ReferencePageRequest page);

    ZoneResponse zone(Authentication actor,long id);

    ReferencePageResponse<VehiculeResponse> vehicules(Authentication actor,ReferencePageRequest page,Long zoneId);

    VehiculeResponse vehicule(Authentication actor,long id);

    ReferencePageResponse<IndisponibiliteResponse> indisponibilites(Authentication actor,ReferencePageRequest page,
                                                                          Long zoneId,Long vehiculeId);

    IndisponibiliteResponse indisponibilite(Authentication actor,long id);

    VehiculeResponse createVehicle(Authentication actor,com.plateforme.covoiturage.dto.request.CreateVehiculeRequest request);

    VehiculeResponse vehicleStatus(Authentication actor,long id,boolean active);

    IndisponibiliteResponse createPeriod(Authentication actor,com.plateforme.covoiturage.dto.request.IndisponibiliteWriteRequest request);

    IndisponibiliteResponse updatePeriod(Authentication actor,long id,com.plateforme.covoiturage.dto.request.IndisponibiliteWriteRequest request);

    void deletePeriod(Authentication actor,long id);
}
