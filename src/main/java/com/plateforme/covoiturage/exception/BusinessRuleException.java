package com.plateforme.covoiturage.exception;
/** Transporte un code métier stable sans exposer de saisie ni de détail de persistance. */
public class BusinessRuleException extends RuntimeException {
 private final String code;
 public BusinessRuleException(String code) {super("Opération incompatible avec les règles métier.");this.code=code;}
 public String getCode(){return code;}
}
