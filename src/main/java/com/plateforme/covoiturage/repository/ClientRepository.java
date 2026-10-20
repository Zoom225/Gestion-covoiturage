package com.plateforme.covoiturage.repository;

import com.plateforme.covoiturage.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {
}
