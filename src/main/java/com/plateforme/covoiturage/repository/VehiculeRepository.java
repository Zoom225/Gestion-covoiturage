package com.plateforme.covoiturage.repository;

import com.plateforme.covoiturage.entity.Vehicule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehiculeRepository extends JpaRepository<Vehicule, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Vehicule> {
    @org.springframework.data.jpa.repository.Query(value="select id from vehicule where id=?1 for update",nativeQuery=true)
    java.util.Optional<Long> lock(Long id);
    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths="zone")
    org.springframework.data.domain.Page<Vehicule> findAll(org.springframework.data.jpa.domain.Specification<Vehicule> specification,
                                                         org.springframework.data.domain.Pageable pageable);
    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths="zone")
    java.util.Optional<Vehicule> findOne(org.springframework.data.jpa.domain.Specification<Vehicule> specification);
}
