package com.plateforme.covoiturage.exception;
public class PaymentUnavailableException extends RuntimeException {
 public PaymentUnavailableException(){super("Validation de paiement indisponible");}
}
