package com.plateforme.covoiturage.repository;
import com.plateforme.covoiturage.entity.PaiementDette;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PaiementDetteRepository extends JpaRepository<PaiementDette,Long> {
 Optional<PaiementDette> findByCleIdempotence(UUID key);

}
