package com.plateforme.covoiturage.repository;

import com.plateforme.covoiturage.entity.Compte;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompteRepository extends JpaRepository<Compte, Long> {
    @EntityGraph(attributePaths = {"client", "client.zone", "zoneAdmin"})
    Optional<Compte> findByIdentifiant(String identifiant);

    @Override
    @EntityGraph(attributePaths = {"client", "client.zone", "zoneAdmin"})
    Optional<Compte> findById(Long id);
}
