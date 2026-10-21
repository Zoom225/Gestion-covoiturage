package com.plateforme.covoiturage.security;

import com.plateforme.covoiturage.dto.response.ProfileResponse;

// Snapshot issu de PostgreSQL, jamais construit à partir des claims de rôle ou de zone.
public record ComptePrincipal(ProfileResponse profile) implements java.security.Principal {
    public Long id() {
        return profile.compteId();
    }

    @Override
    public String getName() {
        return id().toString();
    }
}
