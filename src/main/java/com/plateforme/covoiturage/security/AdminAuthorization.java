package com.plateforme.covoiturage.security;

import com.plateforme.covoiturage.dto.response.ProfileResponse;
import com.plateforme.covoiturage.enums.PerimetreAdmin;
import com.plateforme.covoiturage.enums.RoleTechnique;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("adminAuthorization")
public class AdminAuthorization {
    // Conserve le contrat historique des opérations portant sur une zone non nulle.
    public boolean canManageZone(Authentication authentication, Long resourceZoneId) {
        var profile = adminProfile(authentication);
        return profile != null && resourceZoneId != null
                && (profile.perimetreAdmin() == PerimetreAdmin.GLOBAL
                || (profile.perimetreAdmin() == PerimetreAdmin.LOCAL && resourceZoneId.equals(profile.zoneAdminId())));
    }

    /**
     * Périmètre de consultation des comptes : null signifie GLOBAL réseau ;
     * une zone signifie exclusivement les comptes CLIENT de cette zone.
     * Aucun autre profil n'obtient de périmètre.
     */
    public Long requireAccountReadZone(Authentication authentication) {
        var profile = adminProfile(authentication);
        if (profile != null) {
            if (profile.perimetreAdmin() == PerimetreAdmin.GLOBAL && profile.zoneAdminId() == null) {
                return null;
            }
            if (profile.perimetreAdmin() == PerimetreAdmin.LOCAL && profile.zoneAdminId() != null) {
                return profile.zoneAdminId();
            }
        }
        throw new AccessDeniedException("Accès interdit");
    }


    /** Création : GLOBAL réseau ; LOCAL uniquement LOCAL dans sa zone, sans habilitation supplémentaire. */
    public void requireClientCreation(Authentication actor,
            com.plateforme.covoiturage.enums.CategorieClient category, Long zoneId) {
        Long scope = requireAccountReadZone(actor);
        if (scope != null && (category != com.plateforme.covoiturage.enums.CategorieClient.LOCAL
                || !scope.equals(zoneId))) throw new AccessDeniedException("Accès interdit");
    }

    public void requireGlobal(Authentication actor) {
        if (requireAccountReadZone(actor) != null) throw new AccessDeniedException("Accès interdit");
    }

    /** Même droit que la création ; le service a déjà filtré la visibilité en SQL. */
    public void requireInvitationManagement(Authentication actor, com.plateforme.covoiturage.entity.Compte target) {
        if (target.getRole() == RoleTechnique.ADMIN) {
            requireGlobal(actor);
        } else {
            requireClientCreation(actor, target.getClient().getCategorie(),
                    target.getClient().getZone() == null ? null : target.getClient().getZone().getId());
        }
    }

    private ProfileResponse adminProfile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof ComptePrincipal principal)
                || principal.profile().role() != RoleTechnique.ADMIN) {
            return null;
        }
        return principal.profile();
    }
}
