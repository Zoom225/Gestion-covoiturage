package com.plateforme.covoiturage.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description="Cumuls des données persistées dans le périmètre autorisé, sans filtre temporel implicite. Montants en EUR, paiements simulés validés uniquement.")
public record AdminStatisticsResponse(
        long trajetsPlanifies, long trajetsComplets, long trajetsAnnules,
        long reservationsEnAttente, long reservationsConfirmees, long reservationsAnnulees,
        BigDecimal paiementsPlaces, BigDecimal paiementsDettes, BigDecimal dettesRestantes) {}
