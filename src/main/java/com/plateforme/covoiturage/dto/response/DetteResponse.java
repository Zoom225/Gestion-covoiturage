package com.plateforme.covoiturage.dto.response;
import java.math.BigDecimal;
public record DetteResponse(Long detteId,Long trajetId,BigDecimal montant,BigDecimal regle,String statut) {}
