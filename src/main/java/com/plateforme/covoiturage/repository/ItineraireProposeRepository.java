package com.plateforme.covoiturage.repository;

import com.plateforme.covoiturage.entity.ItinerairePropose;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Lecture du catalogue ; aucun contrat d'écriture exposé par ce repository. */
public interface ItineraireProposeRepository extends Repository<ItinerairePropose,Long> {
    /**
     * Recherche les deux fragments sans casse, avec échappement des caractères LIKE par Spring Data,
     * puis applique le tri et la pagination avant le chargement des résultats.
     */
    Page<ItinerairePropose> findByOrigineContainingIgnoreCaseAndDestinationContainingIgnoreCase(
            String origine, String destination, Pageable pageable);
}
