package com.plateforme.covoiturage.exception;

public class InvalidAccountRequestException extends RuntimeException {
    public InvalidAccountRequestException() { super("Données de compte invalides"); }
}
