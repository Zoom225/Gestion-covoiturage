package com.plateforme.covoiturage.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

/** Contrat applicatif ; implementation dans service.impl. */
public interface InvitationService {
    public record InvitationEmise(@JsonIgnore String jeton, Instant expireA, Long invitationId) {
        @Override public String toString() {
            return "InvitationEmise[jeton=REDACTED]";
        }
    }

    InvitationEmise emettre(Long compteId);

    InvitationEmise renouveler(Long compteId);

    void revoquer(Long compteId);

    Long consommer(String jeton);
}
