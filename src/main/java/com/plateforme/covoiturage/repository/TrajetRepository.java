package com.plateforme.covoiturage.repository;
import com.plateforme.covoiturage.entity.Trajet;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.time.Instant;
import java.util.*;
public interface TrajetRepository extends JpaRepository<Trajet,Long>,JpaSpecificationExecutor<Trajet> {
 @Override @EntityGraph(attributePaths={"zone","vehicule","organisateur"})
 Page<Trajet> findAll(Specification<Trajet> specification,Pageable page);
 @Override @EntityGraph(attributePaths={"zone","vehicule","organisateur"})
 Optional<Trajet> findById(Long id);
 @Query(value="select id from trajet where id=:id for update",nativeQuery=true)
 Optional<Long> lock(@Param("id") long id);
 @Query("select (count(t)>0) from Trajet t where t.vehicule.id=:vehicle and t.statut<>com.plateforme.covoiturage.enums.StatutTrajet.ANNULE and t.depart<:end and t.arrivee>:start")
 boolean overlaps(@Param("vehicle") long vehicle,@Param("start") Instant start,@Param("end") Instant end);
 @Query("select t.id from Trajet t where t.type=com.plateforme.covoiturage.enums.TypeTrajet.PRIVE and t.statut=com.plateforme.covoiturage.enums.StatutTrajet.PLANIFIE and t.depart<:until order by t.id")
 List<Long> privateCandidates(@Param("until") Instant until,Pageable page);
}
