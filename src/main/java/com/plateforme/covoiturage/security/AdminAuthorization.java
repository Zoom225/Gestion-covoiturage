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
    /**
     * Vérifie qu'un administrateur GLOBAL ou LOCAL gère la zone non nulle demandée sans étendre le périmètre LOCAL.
     */
    public boolean canManageZone(Authentication authentication, Long resourceZoneId) {
        var profile = adminProfile(authentication);
        return profile != null && resourceZoneId != null
                && (profile.perimetreAdmin() == PerimetreAdmin.GLOBAL
                || (profile.perimetreAdmin() == PerimetreAdmin.LOCAL && resourceZoneId.equals(profile.zoneAdminId())));
    }

    /**
     * Réserve à GLOBAL tout le réseau et à LOCAL les seuls comptes CLIENT de sa zone, sans périmètre pour les autres profils.
     */
    public Long requireAccountReadZone(Authentication authentication) {
        return requireZoneScope(authentication);
    }

    /**
     * Exige un administrateur de périmètre cohérent ; null représente GLOBAL, un identifiant la seule zone LOCAL autorisée.
     */
    public Long requireZoneScope(Authentication authentication) {
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


    /**
     * Autorise GLOBAL sur le réseau et LOCAL uniquement pour créer un client LOCAL dans sa propre zone.
     */
    public void requireClientCreation(Authentication actor,
            com.plateforme.covoiturage.enums.CategorieClient category, Long zoneId) {
        Long scope = requireAccountReadZone(actor);
        if (scope != null && (category != com.plateforme.covoiturage.enums.CategorieClient.LOCAL
                || !scope.equals(zoneId))) throw new AccessDeniedException("Accès interdit");
    }

    /**
     * Refuse toute opération réservée à l'administration GLOBAL à un acteur LOCAL ou non administrateur.
     */
    public void requireGlobal(Authentication actor) {
        if (requireAccountReadZone(actor) != null) throw new AccessDeniedException("Accès interdit");
    }

    /**
     * Applique au renouvellement d'invitation les droits de création de sa cible, après filtrage de visibilité par le service.
     */
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
