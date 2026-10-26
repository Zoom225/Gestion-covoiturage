package com.plateforme.covoiturage.repository;
import com.plateforme.covoiturage.entity.Reservation;
import com.plateforme.covoiturage.enums.StatutReservation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.util.Optional;
public interface ReservationRepository extends JpaRepository<Reservation,Long> {
 boolean existsByTrajetIdAndClientIdAndStatutNot(Long trajetId,Long clientId,StatutReservation statut);
 boolean existsByTrajetIdAndClientId(Long trajetId,Long clientId);
 long countByTrajetIdAndStatut(Long trajetId,StatutReservation statut);
 @Override @EntityGraph(attributePaths={"trajet","trajet.zone","client"})
 Optional<Reservation> findById(Long id);
 @EntityGraph(attributePaths={"trajet","trajet.zone","client"})
 Page<Reservation> findByClientId(Long id,Pageable page);
}
