package com.plateforme.covoiturage.repository;

import com.plateforme.covoiturage.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    @Query("select i.compte.id from Invitation i where i.empreinte = ?1")
    Optional<Long> findCompteIdByEmpreinte(String empreinte);

    Optional<Invitation> findByEmpreinte(String empreinte);
    boolean existsByCompteIdAndConsommeeAIsNullAndRevoqueeAIsNull(Long compteId);

    @Modifying
    @Query("update Invitation i set i.revoqueeA = ?2 where i.compte.id = ?1"
            + " and i.consommeeA is null and i.revoqueeA is null")
    int revoquerOuvertes(Long compteId, Instant maintenant);

    // Préconditions atomiques en PostgreSQL, même si le contexte JPA contient une ancienne entité.
    @Modifying
    @Query(value = """
            update invitation i set consommee_a = ?2
            where i.empreinte = ?1 and i.consommee_a is null and i.revoquee_a is null
              and i.cree_a <= ?2 and i.expire_a > ?2
              and exists (select 1 from compte c where c.id = i.compte_id and c.etat_identifiants = 'INVITE')
            """, nativeQuery = true)
    int consommerSiValide(String empreinte, Instant maintenant);
}
