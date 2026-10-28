package com.plateforme.covoiturage.repository;
import com.plateforme.covoiturage.entity.Trajet;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.time.Instant;
import java.util.*;
public interface TrajetRepository extends JpaRepository<Trajet,Long>,JpaSpecificationExecutor<Trajet> {
 @Query(value="""
  with perimetre as (
   select id, statut from trajet
    where (cast(:adminScope as bigint) is null or zone_id=:adminScope)
      and (cast(:zoneFilter as bigint) is null or zone_id=:zoneFilter)
  ), inscriptions as (
   select r.id,r.statut from reservation r join perimetre t on t.id=r.trajet_id
  ), dettes as (
   select d.id,d.montant,d.regle from dette_trajet d join perimetre t on t.id=d.trajet_id
  )
  select
   (select count(*) from perimetre where statut='PLANIFIE') as "trajetsPlanifies",
   (select count(*) from perimetre where statut='COMPLET') as "trajetsComplets",
   (select count(*) from perimetre where statut='ANNULE') as "trajetsAnnules",
   (select count(*) from inscriptions where statut='EN_ATTENTE') as "reservationsEnAttente",
   (select count(*) from inscriptions where statut='CONFIRMEE') as "reservationsConfirmees",
   (select count(*) from inscriptions where statut='ANNULEE') as "reservationsAnnulees",
   (select coalesce(sum(p.montant),0) from paiement_place p join inscriptions r on r.id=p.reservation_id) as "paiementsPlaces",
   (select coalesce(sum(p.montant),0) from paiement_dette p join dettes d on d.id=p.dette_id) as "paiementsDettes",
   (select coalesce(sum(montant-regle),0) from dettes) as "dettesRestantes"
  """,nativeQuery=true)
 AdminStatisticsProjection statistics(@Param("adminScope") Long adminScope,@Param("zoneFilter") Long zoneFilter);
 @Override @EntityGraph(attributePaths={"zone","vehicule","organisateur"})
 Page<Trajet> findAll(Specification<Trajet> specification,Pageable page);
 @Override @EntityGraph(attributePaths={"zone","vehicule","organisateur"})
 Optional<Trajet> findById(Long id);
 @Query(value="select id from trajet where id=:id for update",nativeQuery=true)
 Optional<Long> lock(@Param("id") long id);
 @Query("select (count(t)>0) from Trajet t where t.vehicule.id=:vehicle and t.statut<>com.plateforme.covoiturage.enums.StatutTrajet.ANNULE and t.depart<:end and t.arrivee>:start")
 boolean overlaps(@Param("vehicle") long vehicle,@Param("start") Instant start,@Param("end") Instant end);
 @Query("select (count(t)>0) from Trajet t where t.zone.id=:zone and (:vehicle is null or t.vehicule.id=:vehicle) and t.statut<>com.plateforme.covoiturage.enums.StatutTrajet.ANNULE and t.depart<:end and t.arrivee>:start")
 boolean resourceOverlaps(@Param("zone") long zone,@Param("vehicle") Long vehicle,@Param("start") Instant start,@Param("end") Instant end);
 @Query("select t.id from Trajet t where t.id>:after and t.type=com.plateforme.covoiturage.enums.TypeTrajet.PRIVE and t.statut=com.plateforme.covoiturage.enums.StatutTrajet.PLANIFIE and t.depart<:until order by t.id")
 List<Long> j1Candidates(@Param("after") long after,@Param("until") Instant until,Pageable page);
 @Query("select t.id from Trajet t where t.id>:after and t.arrivee<=:now and t.statut in (com.plateforme.covoiturage.enums.StatutTrajet.PLANIFIE,com.plateforme.covoiturage.enums.StatutTrajet.COMPLET) order by t.id")
 List<Long> completionCandidates(@Param("after") long after,@Param("now") Instant now,Pageable page);
}
