package com.plateforme.covoiturage.repository;

import java.math.BigDecimal;
import java.time.Instant;

/** Projection SQL commune ; la nature distingue les identifiants des deux tables. */
public interface PaymentHistoryProjection {
    Long getPaiementId();
    String getNature();
    Long getRessourceId();
    BigDecimal getMontant();
    String getMode();
    Instant getValideLe();
    Instant getRembourseLe();
}
