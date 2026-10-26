package com.plateforme.covoiturage.repository;

import com.plateforme.covoiturage.entity.Indisponibilite;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.*;
import java.util.Optional;

public interface IndisponibiliteRepository extends JpaRepository<Indisponibilite,Long>,
        JpaSpecificationExecutor<Indisponibilite> {
    @Query(value="select id from indisponibilite where id=?1 for update",nativeQuery=true)
    Optional<Long> lock(long id);
    @Query("select (count(p)>0) from Indisponibilite p where (p.zone.id=:zone or p.vehicule.id=:vehicle) and p.debut<:end and p.fin>:start")
    boolean overlaps(@org.springframework.data.repository.query.Param("zone") long zone,
        @org.springframework.data.repository.query.Param("vehicle") long vehicle,
        @org.springframework.data.repository.query.Param("start") java.time.Instant start,
        @org.springframework.data.repository.query.Param("end") java.time.Instant end);
    @Override
    @EntityGraph(attributePaths={"zone","vehicule","vehicule.zone"})
    Page<Indisponibilite> findAll(Specification<Indisponibilite> specification,Pageable pageable);
    @Override
    @EntityGraph(attributePaths={"zone","vehicule","vehicule.zone"})
    Optional<Indisponibilite> findOne(Specification<Indisponibilite> specification);
}
