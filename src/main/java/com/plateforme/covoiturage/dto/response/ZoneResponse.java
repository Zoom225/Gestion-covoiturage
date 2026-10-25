package com.plateforme.covoiturage.dto.response;
public record ZoneResponse(Long zoneId,String nom,boolean actif,String fuseauHoraire,boolean ouverteOccasionnels) {}
