package com.plateforme.covoiturage.mapper;

import com.plateforme.covoiturage.dto.response.CompteAdministrationResponse;
import com.plateforme.covoiturage.entity.Compte;
import org.springframework.stereotype.Component;

@Component
public class CompteMapper {
    public CompteAdministrationResponse toResponse(Compte compte) {
        var client = compte.getClient();
        return new CompteAdministrationResponse(
                compte.getId(), compte.getIdentifiant(), compte.getRole(),
                client == null ? null : client.getId(),
                client == null ? null : client.getNumero(),
                client == null ? null : client.getCategorie(),
                client == null || client.getZone() == null ? null : client.getZone().getId(),
                compte.getPerimetreAdmin(),
                compte.getZoneAdmin() == null ? null : compte.getZoneAdmin().getId(),
                compte.isActif(), client == null ? null : client.isActif(), compte.isAccesEffectif());
    }
}
