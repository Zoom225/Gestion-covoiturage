package com.plateforme.covoiturage.repository;
import com.plateforme.covoiturage.entity.Client;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ZoneAccessRepository extends org.springframework.data.repository.Repository<Client,Long> {
 @Query(value="select exists(select 1 from autorisation_zone_vip where client_id=:client and zone_id=:zone)",nativeQuery=true)
 boolean exists(@Param("client") long client,@Param("zone") long zone);
 @Query(value="select z.id from zone z cross join client c where c.id=?1 and c.actif and z.actif and ((c.categorie='LOCAL' and c.zone_id=z.id) or (c.categorie='OCCASIONNEL' and z.ouverte_occasionnels) or (c.categorie='VIP' and exists(select 1 from autorisation_zone_vip a where a.client_id=c.id and a.zone_id=z.id)))",nativeQuery=true)
 java.util.List<Long> allowedZones(long clientId);
 @Modifying @Query(value="insert into autorisation_zone_vip(client_id,zone_id) values(:client,:zone) on conflict do nothing",nativeQuery=true)
 void grant(@Param("client") long client,@Param("zone") long zone);
 @Modifying @Query(value="delete from autorisation_zone_vip where client_id=:client and zone_id=:zone",nativeQuery=true)
 void revoke(@Param("client") long client,@Param("zone") long zone);
}
