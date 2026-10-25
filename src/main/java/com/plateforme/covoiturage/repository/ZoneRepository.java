package com.plateforme.covoiturage.repository;

import com.plateforme.covoiturage.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneRepository extends JpaRepository<Zone, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Zone> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select z from Zone z where z.id=:id")
    java.util.Optional<Zone> lockById(@org.springframework.data.repository.query.Param("id") long id);
}
