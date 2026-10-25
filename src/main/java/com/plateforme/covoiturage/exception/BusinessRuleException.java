package com.plateforme.covoiturage.exception;
/** Fixed error codes only; no input values or persistence details in responses. */
public class BusinessRuleException extends RuntimeException {
 private final String code;
 public BusinessRuleException(String code) {super("Operation incompatible avec les regles metier");this.code=code;}
 public String getCode(){return code;}
}
