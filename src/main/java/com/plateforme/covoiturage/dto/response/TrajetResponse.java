package com.plateforme.covoiturage.dto.response;
import com.plateforme.covoiturage.enums.*;
import java.time.Instant;
public record TrajetResponse(Long trajetId,Long zoneId,Long vehiculeId,Long organisateurId,
 Instant depart,Instant arrivee,String fuseauHoraire,TypeTrajet type,StatutTrajet statut) {}
