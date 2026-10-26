package com.plateforme.covoiturage.mapper;

import com.plateforme.covoiturage.dto.response.*;
import com.plateforme.covoiturage.entity.*;
import org.springframework.stereotype.Component;

@Component
public class ReferentielMapper {
    public ZoneResponse zone(Zone zone) { return new ZoneResponse(zone.getId(),zone.getNom(),zone.isActif(),zone.getFuseauHoraire(),zone.isOuverteOccasionnels()); }
    public VehiculeResponse vehicule(Vehicule vehicule) {
        return new VehiculeResponse(vehicule.getId(),vehicule.getZone().getId(),vehicule.getZone().getNom(),vehicule.isActif());
    }
    public IndisponibiliteResponse indisponibilite(Indisponibilite period) {
        return new IndisponibiliteResponse(period.getId(),period.getZone()==null?null:period.getZone().getId(),
                period.getVehicule()==null?null:period.getVehicule().getId(),period.getDebut(),period.getFin());
    }
}
