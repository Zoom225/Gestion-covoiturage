package com.plateforme.covoiturage.service.impl;

import com.plateforme.covoiturage.delivery.DeliveryProperties;
import com.plateforme.covoiturage.delivery.InvitationMailTransport;
import com.plateforme.covoiturage.delivery.InvitationPayloadCipher;
import com.plateforme.covoiturage.repository.CompteRepository;
import com.plateforme.covoiturage.repository.InvitationOutboxRepository;
import com.plateforme.covoiturage.service.InvitationDeliveryService;
import com.plateforme.covoiturage.service.InvitationService;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvitationDeliveryServiceImpl implements InvitationDeliveryService {
    private final InvitationPayloadCipher cipher;
    private final InvitationOutboxRepository outbox;
    private final CompteRepository comptes;
    private final InvitationMailTransport transport;
    private final DeliveryProperties properties;
    private final Clock clock;

    /**
     * Ajoute l'invitation chiffrée à l'outbox dans la transaction du compte, sans exposer son jeton au contrat REST.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void enqueue(String recipient, InvitationService.InvitationEmise invitation) {
        UUID id = UUID.randomUUID();
        var encrypted = cipher.encrypt(id, invitation.invitationId(), recipient, invitation.jeton());
        outbox.insert(id, invitation.invitationId(), encrypted, clock.instant());
    }

    /**
     * Traite un seul envoi sous verrou compte puis outbox, annule un envoi devenu obsolète et planifie une reprise sans journaliser de secret en cas d'échec.
     */
    @Transactional
    @Override
    public boolean dispatchNext() {
        var candidate = outbox.next(clock.instant());
        if (candidate.isEmpty()) return false;
        var accountId = outbox.compteId(candidate.get());
        if (accountId.isEmpty()) return false;
        var state = comptes.verrouillerEtatPourInvitation(accountId.get());
        var now = clock.instant(); // Relire après une éventuelle attente du verrou compte.
        var pending = outbox.lock(candidate.get(), now);
        if (pending.isEmpty()) return false;
        var message = pending.get();
        if (!state.orElse("").equals("INVITE") || message.consumed() || message.revoked()
                || !now.isBefore(message.expiresAt())) {
            outbox.cancel(message.id());
            return true;
        }
        try {
            var payload = cipher.decrypt(message.id(), message.invitationId(), message.keyId(),
                    message.nonce(), message.ciphertext());
            transport.send(message.id(), payload.recipient(), payload.token());
            outbox.sent(message.id(), clock.instant());
        } catch (RuntimeException ex) {
            // Aucun message/cause externe : ils peuvent contenir destinataire ou jeton.
            outbox.retry(message.id(), now.plus(properties.retryDelay()));
        }
        return true;
    }
}
