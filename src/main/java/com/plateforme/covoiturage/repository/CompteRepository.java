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
    // Commun aux créations d'administrateurs et à l'amorçage, conservé jusqu'au commit.
    @Query(value = "select 1 from pg_advisory_xact_lock(2423001)", nativeQuery = true)
    Integer verrouillerAdministrationGlobale();

    boolean existsByRoleAndPerimetreAdmin(com.plateforme.covoiturage.enums.RoleTechnique role,
                                          com.plateforme.covoiturage.enums.PerimetreAdmin perimetre);

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

    @Query(value = "select actif from compte where id=?1 for update", nativeQuery = true)
    Optional<Boolean> activiteCompteVerrouillee(Long id);

    @Query(value = "select cl.actif from client cl join compte c on c.client_id=cl.id"
            + " where c.id=?1 for update of cl", nativeQuery = true)
    Optional<Boolean> activiteClientVerrouillee(Long id);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
            update compte c set password_hash=?2,etat_identifiants='INITIALISE'
            where c.id=?1 and c.etat_identifiants='INVITE' and c.actif
              and (c.client_id is null or exists(select 1 from client cl where cl.id=c.client_id and cl.actif))
            """, nativeQuery = true)
    int initialiserSiActif(Long id, String passwordHash);
}
