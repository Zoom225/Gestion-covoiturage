package com.plateforme.covoiturage.dto.response;
import java.math.BigDecimal;
import java.time.Instant;
public record PaymentResponse(Long paiementId,String nature,Long ressourceId,BigDecimal montant,String mode,Instant valideLe,
 BigDecimal montantRembourse,Instant rembourseLe) {}
