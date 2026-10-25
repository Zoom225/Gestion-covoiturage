package com.plateforme.covoiturage.repository;
import com.plateforme.covoiturage.entity.Client;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ZoneAccessRepository extends org.springframework.data.repository.Repository<Client,Long> {
 @Query(value="select exists(select 1 from autorisation_zone_vip where client_id=:client and zone_id=:zone)",nativeQuery=true)
 boolean exists(@Param("client") long client,@Param("zone") long zone);
 @Modifying @Query(value="insert into autorisation_zone_vip(client_id,zone_id) values(:client,:zone) on conflict do nothing",nativeQuery=true)
 void grant(@Param("client") long client,@Param("zone") long zone);
 @Modifying @Query(value="delete from autorisation_zone_vip where client_id=:client and zone_id=:zone",nativeQuery=true)
 void revoke(@Param("client") long client,@Param("zone") long zone);
}
