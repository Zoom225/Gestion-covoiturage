package com.plateforme.covoiturage;
import com.plateforme.covoiturage.entity.*;
import com.plateforme.covoiturage.enums.*;
import com.plateforme.covoiturage.repository.*;
import com.plateforme.covoiturage.service.*;
import com.plateforme.covoiturage.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.*;
import javax.sql.DataSource;
import java.time.*;
import java.net.*;
import java.net.http.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class TrajetIntegrationTests extends JourneyIntegrationSupport {
 @ParameterizedTest @EnumSource(CategorieClient.class)
 void allCategoriesCreateWithoutFakeConfirmation(CategorieClient category)throws Exception{
  Compte actor=switch(category){case VIP->vip;case LOCAL->local;case OCCASIONNEL->occasional;};
  long id=create(actor,TypeTrajet.PUBLIC);
  var booking=reservations.findAll().getFirst();
  assertThat(booking.getStatut()).isEqualTo(StatutReservation.EN_ATTENTE);
  assertThat(booking.getClient().getId()).isEqualTo(actor.getClient().getId());
  assertThat(reservations.countByTrajetIdAndStatut(id,StatutReservation.CONFIRMEE)).isZero();
 }
 @ParameterizedTest @CsvSource({"VIP,30","LOCAL,10","OCCASIONNEL,3"})
 void horizonIsInclusiveAtExactDepartureTime(CategorieClient category,int days){
  var client=switch(category){case VIP->vip.getClient();case LOCAL->local.getClient();case OCCASIONNEL->occasional.getClient();};
  Instant departure=NOW.atZone(ZoneId.of("Europe/Brussels")).plusDays(days).toInstant();
  clock.set(NOW.minusSeconds(1));
  assertThatThrownBy(()->policy.eligible(client,home,departure)).isInstanceOf(com.plateforme.covoiturage.exception.BusinessRuleException.class);
  clock.set(NOW);policy.eligible(client,home,departure);
  assertThatThrownBy(()->policy.eligible(client,home,NOW)).isInstanceOf(com.plateforme.covoiturage.exception.BusinessRuleException.class);
 }
 @Test void daylightSavingUsesZoneCalendarWithDepartureHour(){
  Instant departure=Instant.parse("2027-03-29T10:00:00Z");
  var open=departure.atZone(ZoneId.of("Europe/Brussels")).minusDays(3).toInstant();
  clock.set(open);policy.eligible(occasional.getClient(),home,departure);
  clock.set(open.minusSeconds(1));
  assertThatThrownBy(()->policy.eligible(occasional.getClient(),home,departure)).isInstanceOf(com.plateforme.covoiturage.exception.BusinessRuleException.class);
 }
 @ParameterizedTest @ValueSource(strings={"anonymous","admin","wrongLocal","vipNoGrant","inactive"})
 void unauthorizedOrOutOfScopeCannotCreate(String kind)throws Exception{
  Compte actor=switch(kind){case "admin"->global;case "wrongLocal"->otherLocal;case "vipNoGrant"->ungrantedVip;case "inactive"->local;default->null;};
  if(kind.equals("inactive"))jdbc.update("update compte set actif=false where id=?",local.getId());
  var result=call("POST","/api/trajets",actor,request(NOW.plusSeconds(172800),NOW.plusSeconds(176400),TypeTrajet.PUBLIC));
  assertThat(result.statusCode()).isEqualTo(actor==null||kind.equals("inactive")?401:403);
  assertThat(trips.count()).isZero();assertThat(reservations.count()).isZero();
 }
 @ParameterizedTest @ValueSource(strings={"equal","reversed","past","unavailable","zoneInactive","mismatchedVehicle"})
 void invalidTripCannotPartiallyCreate(String kind)throws Exception{
  Instant start=NOW.plusSeconds(172800),end=start.plusSeconds(3600);
  Map<String,Object> input=new HashMap<>(request(start,end,TypeTrajet.PUBLIC));
  switch(kind){
   case "equal"->input.put("arrivee",start.toString());
   case "reversed"->input.put("arrivee",start.minusSeconds(1).toString());
   case "past"->{input.put("depart",NOW.minusSeconds(3600).toString());input.put("arrivee",NOW.minusSeconds(1).toString());}
   case "unavailable"->jdbc.update("insert into indisponibilite(zone_id,debut,fin) values(?,?,?)",home.getId(),Timestamp.from(start),Timestamp.from(end));
   case "zoneInactive"->jdbc.update("update zone set actif=false where id=?",home.getId());
   case "mismatchedVehicle"->input.put("zoneId",away.getId());
  }
  var result=call("POST","/api/trajets",local,input);
  assertThat(result.statusCode()).isEqualTo(switch(kind){case "equal","reversed"->400;case "zoneInactive"->403;default->409;});
  assertThat(trips.count()).isZero();assertThat(reservations.count()).isZero();
 }
 @Test void organizerDebtDoesNotInventAnExtraBanOnManagingExistingPrivateTrip()throws Exception{
  long id=create(local,TypeTrajet.PRIVE);
  var previousTrip=new HashMap<>(request(NOW.plusSeconds(259200),NOW.plusSeconds(262800),TypeTrajet.PUBLIC));
  previousTrip.put("vehiculeId",secondVehicle.getId());
  long debtTrip=read(call("POST","/api/trajets",local,previousTrip)).path("trajetId").asLong();
  jdbc.update("insert into dette_trajet(trajet_id,client_id) values(?,?)",debtTrip,local.getClient().getId());
  assertThat(call("POST","/api/trajets/"+id+"/participants",local,Map.of("clientId",vip.getClient().getId())).statusCode()).isEqualTo(201);
 }
 @Test void publicBookingIsPersonalPendingAndUnique()throws Exception{
  long id=create(local,TypeTrajet.PUBLIC);
  var result=call("POST","/api/trajets/"+id+"/reservations",vip,null);
  assertThat(result.statusCode()).isEqualTo(201);
  assertThat(read(result).path("clientId").asLong()).isEqualTo(vip.getClient().getId());
  assertThat(read(result).path("statut").asText()).isEqualTo("EN_ATTENTE");
  assertThat(call("POST","/api/trajets/"+id+"/reservations",vip,null).statusCode()).isEqualTo(409);
  assertThat(call("POST","/api/trajets/"+id+"/reservations",otherLocal,null).statusCode()).isEqualTo(403);
  assertThat(call("POST","/api/trajets/"+Long.MAX_VALUE+"/reservations",vip,null).statusCode()).isEqualTo(404);
 }
 @Test void privateParticipantsRequireOwnershipAndNeverConfirmOnAdd()throws Exception{
  long id=create(local,TypeTrajet.PRIVE);
  var body=Map.of("clientId",vip.getClient().getId());
  assertThat(call("POST","/api/trajets/"+id+"/participants",vip,body).statusCode()).isEqualTo(403);
  assertThat(call("POST","/api/trajets/"+id+"/reservations",vip,null).statusCode()).isEqualTo(403);
  var result=call("POST","/api/trajets/"+id+"/participants",local,body);
  assertThat(result.statusCode()).isEqualTo(201);assertThat(read(result).path("statut").asText()).isEqualTo("EN_ATTENTE");
  assertThat(call("POST","/api/trajets/"+id+"/participants",local,Map.of("clientId",otherLocal.getClient().getId())).statusCode()).isEqualTo(403);
 }
 @Test void pendingDoesNotReserveOneOfFourConfirmedPlaces()throws Exception{
  long id=create(local,TypeTrajet.PUBLIC);
  for(int i=1;i<=6;i++){
   var actor=account("O9801"+i,CategorieClient.OCCASIONNEL,null);
   assertThat(call("POST","/api/trajets/"+id+"/reservations",actor,null).statusCode()).isEqualTo(201);
  }
  assertThat(reservations.count()).isEqualTo(7);
  assertThat(reservations.countByTrajetIdAndStatut(id,StatutReservation.CONFIRMEE)).isZero();
 }
 @ParameterizedTest @ValueSource(strings={"COMPLET","ANNULE"})
 void closedTripRejectsNewBookings(String status)throws Exception{
  long id=create(local,TypeTrajet.PUBLIC);jdbc.update("update trajet set statut=? where id=?",status,id);
  assertThat(call("POST","/api/trajets/"+id+"/reservations",vip,null).statusCode()).isEqualTo(409);
 }
 @Test void activeDebtBlocksCreateReserveAndBeingAdded()throws Exception{
  long id=create(local,TypeTrajet.PRIVE);
  var debtTripInput=new HashMap<>(request(NOW.plusSeconds(172800),NOW.plusSeconds(176400),TypeTrajet.PUBLIC));
  debtTripInput.put("vehiculeId",secondVehicle.getId());
  long debtTripId=read(call("POST","/api/trajets",vip,debtTripInput)).path("trajetId").asLong();
  jdbc.update("insert into dette_trajet(trajet_id,client_id) values(?,?)",debtTripId,vip.getClient().getId());
  assertThat(call("POST","/api/trajets/"+id+"/participants",local,Map.of("clientId",vip.getClient().getId())).statusCode()).isEqualTo(409);
  var input=new HashMap<>(request(NOW.plusSeconds(259200),NOW.plusSeconds(262800),TypeTrajet.PUBLIC));input.put("vehiculeId",secondVehicle.getId());
  assertThat(call("POST","/api/trajets",vip,input).statusCode()).isEqualTo(409);
  jdbc.update("update trajet set type='PUBLIC' where id=?",id);
  assertThat(call("POST","/api/trajets/"+id+"/reservations",vip,null).statusCode()).isEqualTo(409);
 }
 @ParameterizedTest @ValueSource(ints={-1,0,899,900})
 void vehicleNeedsFifteenMinutesBetweenTrips(int gap)throws Exception{
  create(local,TypeTrajet.PUBLIC);
  Instant start=NOW.plusSeconds(176400+gap);
  var result=call("POST","/api/trajets",local,request(start,start.plusSeconds(3600),TypeTrajet.PUBLIC));
  assertThat(result.statusCode()).isEqualTo(gap>=900?201:409);
 }
 @Test void concurrentVehicleCreationsAndDuplicateBookingsRemainAtomic()throws Exception{
  try(var pool=Executors.newFixedThreadPool(2)){
   var start=new CountDownLatch(1);
   Callable<Integer> task=()->{start.await();return call("POST","/api/trajets",local,request(NOW.plusSeconds(172800),NOW.plusSeconds(176400),TypeTrajet.PUBLIC)).statusCode();};
   var a=pool.submit(task);var b=pool.submit(task);start.countDown();
   assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
   assertThat(trips.count()).isEqualTo(1);assertThat(reservations.count()).isEqualTo(1);
   long id=trips.findAll().getFirst().getId();
   var bookingStart=new CountDownLatch(1);
   Callable<Integer> book=()->{bookingStart.await();return call("POST","/api/trajets/"+id+"/reservations",vip,null).statusCode();};
   var c=pool.submit(book);var d=pool.submit(book);bookingStart.countDown();
   assertThat(List.of(c.get(30,TimeUnit.SECONDS),d.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
  }
 }
 @Test void listsAndCountsNeverLeakUnauthorizedZonesOrPrivateTrips()throws Exception{
  long privateId=create(local,TypeTrajet.PRIVE);
  var publicInput=new HashMap<>(request(NOW.plusSeconds(172800),NOW.plusSeconds(176400),TypeTrajet.PUBLIC));publicInput.put("vehiculeId",secondVehicle.getId());
  assertThat(call("POST","/api/trajets",local,publicInput).statusCode()).isEqualTo(201);
  var page=read(call("GET","/api/trajets?size=1",vip,null));
  assertThat(page.path("totalElements").asLong()).isEqualTo(1);
  assertThat(read(call("GET","/api/trajets",otherLocal,null)).path("totalElements").asLong()).isZero();
  assertThat(call("GET","/api/trajets/"+privateId,vip,null).statusCode()).isEqualTo(404);
  assertThat(call("GET","/api/trajets/"+privateId,local,null).statusCode()).isEqualTo(200);
  String response=call("GET","/api/reservations/me",vip,null).body();
  assertThat(response).doesNotContain("passwordHash","invitationEmail","accessToken");
  assertThat(read(call("GET","/api/reservations/me",vip,null)).path("totalElements").asLong()).isZero();
 }
 @Test void postgresChecksDatesZoneVehicleAndForeignKeys(){
  assertThatThrownBy(()->jdbc.update("insert into trajet(zone_id,vehicule_id,organisateur_id,depart,arrivee,type,origine,destination) values(?,?,?,?,?,'PUBLIC','Gare A','Gare B')",
   home.getId(),vehicle.getId(),local.getClient().getId(),Timestamp.from(NOW),Timestamp.from(NOW)))
   .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("insert into trajet(zone_id,vehicule_id,organisateur_id,depart,arrivee,type,origine,destination) values(?,?,?,?,?,'PUBLIC','Gare A','Gare B')",
   away.getId(),vehicle.getId(),local.getClient().getId(),Timestamp.from(NOW),Timestamp.from(NOW.plusSeconds(3600))))
   .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
 @Test void migrationV10ToV11PreservesExistingData(){
  String schema="trip_upgrade";
  org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("10").load().migrate();
  jdbc.update("insert into "+schema+".zone(id,nom,fuseau_horaire) values(1,'Preserved','Europe/Brussels')");
  jdbc.update("insert into "+schema+".vehicule(id,zone_id) values(1,1)");
  var upgraded=org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("11").load();
  assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
  assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
  assertThat(jdbc.queryForObject("select nom from "+schema+".zone where id=1",String.class)).isEqualTo("Preserved");
  assertThat(jdbc.queryForObject("select count(*) from "+schema+".trajet",Integer.class)).isZero();
 }
 @Test void swaggerDocumentsPendingBookingsAndNoUndefinedMutations()throws Exception{
  var paths=read(call("GET","/v3/api-docs",null,null)).path("paths");
  assertThat(paths.path("/api/trajets").has("post")).isTrue();
  assertThat(paths.path("/api/trajets/{id}/reservations").has("post")).isTrue();
  assertThat(paths.path("/api/trajets/{id}").has("delete")).isFalse();
  assertThat(paths.path("/api/trajets/{id}").has("put")).isFalse();
 }
}
