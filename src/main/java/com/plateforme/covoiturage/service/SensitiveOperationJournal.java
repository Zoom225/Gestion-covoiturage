package com.plateforme.covoiturage.service;

import com.plateforme.covoiturage.security.ComptePrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;

/** Événements de réussite après commit ; aucun texte fourni par l'utilisateur. */
@Component
@RequiredArgsConstructor
@Slf4j
public class SensitiveOperationJournal {
    public enum Action {
        PLACE_PAYEE, DETTE_REGLEE, DETTE_REGULARISEE, RESERVATION_CONFIRMEE, TRAJET_COMPLET,
        TRAJET_CONVERTI_PUBLIC, DETTE_CREEE, ZONE_CREEE, ZONE_CONFIGUREE,
        ZONE_ACTIVEE, ZONE_DESACTIVEE, AUTORISATION_VIP_ACCORDEE, AUTORISATION_VIP_RETIREE,
        COMPTE_CREE, COMPTE_ACTIVE, INVITATION_RENOUVELEE
    }
    public enum Resource { PAIEMENT_PLACE, PAIEMENT_DETTE, RESERVATION, TRAJET, DETTE, ZONE, CLIENT, COMPTE }
    private final Clock clock;

    public void record(Action action, Authentication actor, Resource resource, long resourceId) {
        record(action,actor,resource,resourceId,null);
    }

    public void record(Action action, Authentication actor, Resource resource, long resourceId, Long relatedId) {
        Long actorId = actor != null && actor.getPrincipal() instanceof ComptePrincipal principal
                ? principal.id() : null;
        recordByAccount(action,actorId,resource,resourceId,relatedId);
    }

    /** Identité établie côté serveur, notamment après consommation d'une invitation. */
    public void recordByAccount(Action action, Long actorId, Resource resource, long resourceId, Long relatedId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("La journalisation exige une transaction active");
        }
        var occurredAt = clock.instant();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                log.info("operation_sensible action={} acteur={} ressource={} id={} reference={} date={}",
                        action, actorId == null ? "SYSTEME" : actorId, resource, resourceId, relatedId, occurredAt);
            }
        });
    }
}
