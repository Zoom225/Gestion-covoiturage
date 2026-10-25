package com.plateforme.covoiturage.exception;

public class DeliveryUnavailableException extends RuntimeException {
    public DeliveryUnavailableException() {
        super("Livraison d'invitation indisponible");
    }
}
