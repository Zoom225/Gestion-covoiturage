package com.plateforme.covoiturage.repository;
import com.plateforme.covoiturage.entity.PaiementPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PaiementPlaceRepository extends JpaRepository<PaiementPlace,Long> {
 Optional<PaiementPlace> findByCleIdempotence(UUID key);
 Optional<PaiementPlace> findByReservationId(Long id);
 @org.springframework.data.jpa.repository.Query(value="""
  select p.id as "paiementId", 'PLACE' as nature, p.reservation_id as "ressourceId",
         p.montant, p.mode, p.valide_le as "valideLe"
    from paiement_place p join reservation r on r.id=p.reservation_id where r.client_id=:clientId
  union all
  select p.id as "paiementId", 'DETTE' as nature, p.dette_id as "ressourceId",
         p.montant, p.mode, p.valide_le as "valideLe"
    from paiement_dette p join dette_trajet d on d.id=p.dette_id where d.client_id=:clientId
  order by "valideLe" desc, nature asc, "paiementId" desc
  """,countQuery="""
  select (select count(*) from paiement_place p join reservation r on r.id=p.reservation_id where r.client_id=:clientId)
       + (select count(*) from paiement_dette p join dette_trajet d on d.id=p.dette_id where d.client_id=:clientId)
  """,nativeQuery=true)
 org.springframework.data.domain.Page<PaymentHistoryProjection> history(
   @org.springframework.data.repository.query.Param("clientId") long clientId,
   org.springframework.data.domain.Pageable page);
}
