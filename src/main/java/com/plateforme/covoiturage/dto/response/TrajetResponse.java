package com.plateforme.covoiturage.dto.response;
import com.plateforme.covoiturage.enums.*;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;
public record TrajetResponse(Long trajetId,Long zoneId,Long vehiculeId,Long organisateurId,
 Instant depart,Instant arrivee,String fuseauHoraire,TypeTrajet type,StatutTrajet statut,
 @Schema(description="Origine géographique ; null pour un ancien trajet non renseigné",nullable=true) String origine,
 @Schema(description="Destination géographique ; null pour un ancien trajet non renseigné",nullable=true) String destination) {}
