package com.plateforme.covoiturage.repository;

import com.plateforme.covoiturage.entity.Indisponibilite;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.*;
import java.util.Optional;

public interface IndisponibiliteRepository extends JpaRepository<Indisponibilite,Long>,
        JpaSpecificationExecutor<Indisponibilite> {
    @Override
    @EntityGraph(attributePaths={"zone","vehicule","vehicule.zone"})
    Page<Indisponibilite> findAll(Specification<Indisponibilite> specification,Pageable pageable);
    @Override
    @EntityGraph(attributePaths={"zone","vehicule","vehicule.zone"})
    Optional<Indisponibilite> findOne(Specification<Indisponibilite> specification);
}
