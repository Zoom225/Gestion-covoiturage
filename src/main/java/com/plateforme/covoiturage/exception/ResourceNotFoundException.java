package com.plateforme.covoiturage.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException() {
        super("Ressource introuvable");
    }
}
