package com.plateforme.covoiturage.service;

/** Contrat applicatif ; implementation dans service.impl. */
public interface InvitationDeliveryService {
    void enqueue(String recipient, InvitationService.InvitationEmise invitation);

    boolean dispatchNext();
}
