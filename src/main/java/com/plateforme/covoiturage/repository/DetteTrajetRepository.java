package com.plateforme.covoiturage.repository;
import com.plateforme.covoiturage.entity.DetteTrajet;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.util.Optional;
public interface DetteTrajetRepository extends JpaRepository<DetteTrajet,Long> {
 boolean existsByClientIdAndStatut(Long clientId,String statut);
 boolean existsByTrajetId(Long trajetId);
 @Override @EntityGraph(attributePaths={"client","trajet","trajet.zone"})
 Optional<DetteTrajet> findById(Long id);
 @EntityGraph(attributePaths={"client","trajet","trajet.zone"})
 Page<DetteTrajet> findByClientId(Long clientId,Pageable page);
 @Query(value="select id from dette_trajet where id=?1 for update",nativeQuery=true)
 Optional<Long> lock(Long id);
}
