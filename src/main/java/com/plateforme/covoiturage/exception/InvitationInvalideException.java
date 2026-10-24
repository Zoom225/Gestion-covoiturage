package com.plateforme.covoiturage.exception;

public class InvitationInvalideException extends RuntimeException {
    public InvitationInvalideException() {
        super("Invitation indisponible ou invalide");
    }
}
