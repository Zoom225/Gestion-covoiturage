package com.plateforme.covoiturage.security;

import com.plateforme.covoiturage.enums.PerimetreAdmin;
import com.plateforme.covoiturage.enums.RoleTechnique;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("adminAuthorization")
public class AdminAuthorization {
    // À réutiliser dans les services administratifs avec la zone de la ressource lue en base.
    public boolean canManageZone(Authentication authentication, Long resourceZoneId) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof ComptePrincipal principal)) {
            return false;
        }
        var profile = principal.profile();
        return profile.role() == RoleTechnique.ADMIN && resourceZoneId != null
                && (profile.perimetreAdmin() == PerimetreAdmin.GLOBAL
                || (profile.perimetreAdmin() == PerimetreAdmin.LOCAL && resourceZoneId.equals(profile.zoneAdminId())));
    }
}
