package com.plateforme.covoiturage.repository;

import com.plateforme.covoiturage.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Client c where c.id=?1")
    java.util.Optional<Client> lockById(Long id);
}
