package com.plateforme.covoiturage.repository;
import com.plateforme.covoiturage.entity.PaiementPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PaiementPlaceRepository extends JpaRepository<PaiementPlace,Long> {
 Optional<PaiementPlace> findByCleIdempotence(UUID key);
 Optional<PaiementPlace> findByReservationId(Long id);
}
