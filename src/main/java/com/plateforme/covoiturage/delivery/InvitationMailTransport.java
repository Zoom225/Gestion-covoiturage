package com.plateforme.covoiturage.delivery;

import java.util.UUID;

/** Canal interne uniquement. Ne jamais journaliser les arguments. */
public interface InvitationMailTransport {
    void send(UUID messageId, String recipient, String token);
}
