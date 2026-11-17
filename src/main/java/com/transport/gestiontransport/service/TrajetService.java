package com.transport.gestiontransport.service;

import com.transport.gestiontransport.dto.request.TrajetRequest;
import com.transport.gestiontransport.entity.Trajet;
import com.transport.gestiontransport.dto.request.CreateTrajetRequest;
import com.transport.gestiontransport.dto.response.TrajetDto;

import java.util.List;
import java.math.BigDecimal;
import com.transport.gestiontransport.entity.enums.VilleBelge;
import java.time.LocalDate;

public interface TrajetService {
    void checkReadAccess(Long trajetId);

    int REQUIRED_PASSAGERS_PER_TRAJET = Trajet.MAX_CLIENTS;
    BigDecimal PRIX_CLIENT_EUROS = new BigDecimal("25.00");
    BigDecimal PRIX_TRAJET_EUROS = PRIX_CLIENT_EUROS.multiply(BigDecimal.valueOf(REQUIRED_PASSAGERS_PER_TRAJET));

    static boolean estQualifiePourCovoiturage(Trajet trajet) {
        return trajet.getChauffeur() != null && trajet.getVilleDepart() != null
                && trajet.getVilleArrivee() != null && trajet.getVilleDepart() != trajet.getVilleArrivee()
                && Double.valueOf(PRIX_CLIENT_EUROS.doubleValue()).equals(trajet.getPrixParPassager())
                && Double.valueOf(PRIX_TRAJET_EUROS.doubleValue()).equals(trajet.getPrixTotal());
    }

    TrajetDto createTrajet(CreateTrajetRequest request, String username);
    TrajetDto createTrajetForAdmin(CreateTrajetRequest request);
    List<TrajetDto> searchPublicTrajets(VilleBelge depart, VilleBelge arrivee, LocalDate date);

    List<TrajetDto> findAllTrajets();

    List<TrajetDto> getPublicAvailableTrajets();

    List<TrajetDto> findByOrganisateur(Long organisateurId);

    List<TrajetDto> findByAgence(Long agenceId);

    Trajet getTrajetEntityById(Long id);

    TrajetDto getTrajetDtoById(Long id);

    TrajetDto updateTrajet(Long trajetId, TrajetRequest request);

    void cancelTrajet(Long trajetId, Long requesterId);

    void convertToPublic(Long trajetId);

    void checkAndConvertExpiredPrivateTrajets();

    /**
     * Règle métier : impute la pénalité équivalente aux 4 places (100€) à l'organisateur/chauffeur
     * lorsqu'un trajet privé arrive à échéance en restant incomplet sans conversion publique autorisée.
     */
    void imputerPenaliteTrajetPriveIncomplet(Long trajetId);
}
