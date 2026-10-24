package com.plateforme.covoiturage.repository;

import com.plateforme.covoiturage.entity.Compte;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;


import java.util.Optional;

public interface CompteRepository extends JpaRepository<Compte, Long>, JpaSpecificationExecutor<Compte> {
    @EntityGraph(attributePaths = {"client", "client.zone", "zoneAdmin"})
    // Use the same PostgreSQL lower() expression as the unique index in V4.
    @Query("select compte from Compte compte where lower(compte.identifiant) = lower(?1)")
    Optional<Compte> findByIdentifiantIgnoringCase(String identifiant);

    // Valeur scalaire fraîche et verrou de ligne commun, sans cache JPA ni jointure externe.
    @Query(value = "select etat_identifiants from compte where id = ?1 for update", nativeQuery = true)
    Optional<String> verrouillerEtatPourInvitation(Long id);
    @Override
    @EntityGraph(attributePaths = {"client", "client.zone", "zoneAdmin"})
    Optional<Compte> findById(Long id);
    @Override
    @EntityGraph(attributePaths = {"client", "client.zone", "zoneAdmin"})
    Page<Compte> findAll(Specification<Compte> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"client", "client.zone", "zoneAdmin"})
    Optional<Compte> findOne(Specification<Compte> specification);
}
