package com.plateforme.covoiturage.exception;

public class InvitationConflictException extends RuntimeException {
    public InvitationConflictException() {
        super("Opération d'invitation incompatible avec l'état courant");
    }
}
