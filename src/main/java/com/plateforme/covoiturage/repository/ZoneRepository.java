package com.plateforme.covoiturage.repository;

import com.plateforme.covoiturage.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneRepository extends JpaRepository<Zone, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Zone> {
}
