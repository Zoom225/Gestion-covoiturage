package com.plateforme.covoiturage.repository;

import com.plateforme.covoiturage.entity.Compte;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CompteRepository extends JpaRepository<Compte, Long> {
    @EntityGraph(attributePaths = {"client", "client.zone", "zoneAdmin"})
    // Use the same PostgreSQL lower() expression as the unique index in V4.
    @Query("select compte from Compte compte where lower(compte.identifiant) = lower(?1)")
    Optional<Compte> findByIdentifiantIgnoringCase(String identifiant);

    @Override
    @EntityGraph(attributePaths = {"client", "client.zone", "zoneAdmin"})
    Optional<Compte> findById(Long id);
}
