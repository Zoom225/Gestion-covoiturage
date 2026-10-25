package com.plateforme.covoiturage.repository;

import com.plateforme.covoiturage.entity.Vehicule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehiculeRepository extends JpaRepository<Vehicule, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Vehicule> {
    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths="zone")
    org.springframework.data.domain.Page<Vehicule> findAll(org.springframework.data.jpa.domain.Specification<Vehicule> specification,
                                                         org.springframework.data.domain.Pageable pageable);
    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths="zone")
    java.util.Optional<Vehicule> findOne(org.springframework.data.jpa.domain.Specification<Vehicule> specification);
}
