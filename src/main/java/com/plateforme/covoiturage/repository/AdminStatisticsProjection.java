package com.plateforme.covoiturage.repository;

import java.math.BigDecimal;

public interface AdminStatisticsProjection {
    long getTrajetsPlanifies();
    long getTrajetsComplets();
    long getTrajetsAnnules();
    long getReservationsEnAttente();
    long getReservationsConfirmees();
    long getReservationsAnnulees();
    BigDecimal getPaiementsPlaces();
    BigDecimal getPaiementsDettes();
    BigDecimal getDettesRestantes();
}
