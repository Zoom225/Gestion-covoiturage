package com.plateforme.covoiturage.repository;
import com.plateforme.covoiturage.entity.Reservation;
import com.plateforme.covoiturage.enums.StatutReservation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.repository.query.Param;
public interface ReservationRepository extends JpaRepository<Reservation,Long> {
 boolean existsByTrajetIdAndClientIdAndStatutNot(Long trajetId,Long clientId,StatutReservation statut);
 boolean existsByTrajetIdAndClientId(Long trajetId,Long clientId);
 long countByTrajetIdAndStatut(Long trajetId,StatutReservation statut);
 java.util.List<Reservation> findByTrajetIdAndStatut(Long trajetId,StatutReservation statut);
 /** Une ancienne annulation payée, non datée, peut changer le résultat à l'échéance. */
 @Query("""
  select (count(p)>0) from PaiementPlace p join p.reservation r
  where r.trajet.id=:tripId and p.valideLe<=:deadline
   and r.statut=com.plateforme.covoiturage.enums.StatutReservation.ANNULEE
   and (r.annuleLe is null or r.annuleLe<p.valideLe)
  """)
 boolean hasUnverifiableJ1History(@Param("tripId") long tripId,@Param("deadline") Instant deadline);
 /** Intervalle actif : paiement inclus, annulation exclue. Aucun compteur actuel n'est utilisé. */
 @Query("""
  select count(p) from PaiementPlace p join p.reservation r
  where r.trajet.id=:tripId and p.valideLe<=:deadline
   and (r.statut=com.plateforme.covoiturage.enums.StatutReservation.CONFIRMEE
    or (r.statut=com.plateforme.covoiturage.enums.StatutReservation.ANNULEE and r.annuleLe>:deadline))
  """)
 long countConfirmedAtJ1(@Param("tripId") long tripId,@Param("deadline") Instant deadline);
 @Override @EntityGraph(attributePaths={"trajet","trajet.zone","client"})
 Optional<Reservation> findById(Long id);
 @EntityGraph(attributePaths={"trajet","trajet.zone","client"})
 Page<Reservation> findByClientId(Long id,Pageable page);
}
