package com.plateforme.covoiturage;
import com.plateforme.covoiturage.entity.*;
import com.plateforme.covoiturage.enums.*;
import com.plateforme.covoiturage.repository.*;
import com.plateforme.covoiturage.service.*;
import com.plateforme.covoiturage.payment.PaymentGateway;
import com.plateforme.covoiturage.exception.*;
import com.plateforme.covoiturage.scheduling.J1Scheduler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.scheduling.annotation.Scheduled;
import java.net.URI;
import java.net.http.*;
import java.math.BigDecimal;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestPropertySource(properties={"driveconnect.scheduling.j1.enabled=false",
 "driveconnect.security.cors.allowed-origins=http://localhost:4200"})
class PaymentIntegrationTests extends JourneyIntegrationSupport {
 @MockitoBean PaymentGateway gateway;
 @org.springframework.test.context.bean.override.mockito.MockitoSpyBean AvailabilityService availability;
 @Autowired PaiementPlaceRepository seatPayments;
 @Autowired PaiementDetteRepository debtPayments;
 @Autowired DetteTrajetRepository debts;
 @Autowired J1ProcessingService j1;
 @BeforeEach void validationControlledOnlyByServer(){
  reset(gateway);when(gateway.validate(any(),any())).thenReturn(PaymentGateway.Mode.SIMULATION);
 }
 @Override void cleanBusiness(){
  tx.executeWithoutResult(status->{
   jdbc.update("delete from paiement_place");jdbc.update("delete from paiement_dette");super.cleanBusiness();
  });
 }
 HttpResponse<String> pay(String path,Compte actor,UUID key,Object body)throws Exception{
  return pay(path,actor,key,body,null);
 }
 HttpResponse<String> pay(String path,Compte actor,UUID key,Object body,String origin)throws Exception{
  var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
   .header("Content-Type","application/json");
  if(origin!=null)request.header("Origin",origin);
  if(key!=null)request.header("Idempotency-Key",key.toString());
  if(actor!=null)request.header("Authorization","Bearer "+new TokenService(encoder,jwtProperties,Clock.systemUTC()).issue(actor.getId()).accessToken());
  return http.send(request.POST(HttpRequest.BodyPublishers.ofString(body==null?"":json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
 }
 String seat(long id){return "/api/reservations/"+id+"/paiements";}
 String debt(long id){return "/api/dettes/"+id+"/paiements";}
 long booking(long trip,Compte actor){
  return jdbc.queryForObject("select id from reservation where trajet_id=? and client_id=?",Long.class,trip,actor.getClient().getId());
 }
 long reserve(long trip,Compte actor)throws Exception{
  var result=call("POST","/api/trajets/"+trip+"/reservations",actor,null);
  assertThat(result.statusCode()).isEqualTo(201);return read(result).path("reservationId").asLong();
 }
 Instant deadline(){
  return NOW.plusSeconds(172800).atZone(ZoneId.of("Europe/Brussels")).toLocalDate().minusDays(1)
   .atTime(0,15).atZone(ZoneId.of("Europe/Brussels")).toInstant();
 }
 long generateDebt()throws Exception{
  long id=create(local,TypeTrajet.PRIVE);clock.set(deadline());
  assertThat(j1.catchUp()).isEqualTo(1);
  return debts.findAll().getFirst().getId();
 }
 @Test void organizerConfirmationRequiresTwentyEuroValidatedPayment()throws Exception{
  long id=create(local,TypeTrajet.PUBLIC);long reservation=booking(id,local);
  assertThat(reservations.findById(reservation).orElseThrow().getStatut()).isEqualTo(StatutReservation.EN_ATTENTE);
  var result=pay(seat(reservation),local,UUID.randomUUID(),null);
  assertThat(result.statusCode()).isEqualTo(200);
  assertThat(read(result).path("montant").decimalValue()).isEqualByComparingTo("20.00");
  assertThat(read(result).path("mode").asText()).isEqualTo("SIMULATION");
  assertThat(reservations.findById(reservation).orElseThrow().getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
  assertThat(result.body()).doesNotContain("password","hash","cleIdempotence","accessToken");
 }
 @Test void publicAndPrivateParticipantsPayTheirOwnPlace()throws Exception{
  long id=create(local,TypeTrajet.PRIVE);
  assertThat(call("POST","/api/trajets/"+id+"/participants",local,Map.of("clientId",vip.getClient().getId())).statusCode()).isEqualTo(201);
  long reservation=booking(id,vip);
  assertThat(pay(seat(reservation),local,UUID.randomUUID(),null).statusCode()).isEqualTo(404);
  assertThat(pay(seat(reservation),vip,UUID.randomUUID(),null).statusCode()).isEqualTo(200);
  // D4: the first validated payer can be a participant; organizer is still pending.
  assertThat(reservations.findById(booking(id,local)).orElseThrow().getStatut()).isEqualTo(StatutReservation.EN_ATTENTE);
 }
 @Test void idempotentReplayDoesNotChargeTwiceAndNewKeyCannotRepay()throws Exception{
  long id=create(local,TypeTrajet.PUBLIC);long reservation=booking(id,local);UUID key=UUID.randomUUID();
  var first=pay(seat(reservation),local,key,null);var second=pay(seat(reservation),local,key,null);
  assertThat(first.statusCode()).isEqualTo(200);assertThat(second.statusCode()).isEqualTo(200);
  assertThat(read(first).path("paiementId").asLong()).isEqualTo(read(second).path("paiementId").asLong());
  assertThat(pay(seat(reservation),local,UUID.randomUUID(),null).statusCode()).isEqualTo(409);
  assertThat(seatPayments.count()).isEqualTo(1);
  verify(gateway,times(1)).validate(eq(key),any());
 }
 @Test void sameIdempotencyKeyCannotPayAnotherReservation()throws Exception{
  long id=create(local,TypeTrajet.PUBLIC);long a=booking(id,local),b=reserve(id,vip);UUID key=UUID.randomUUID();
  assertThat(pay(seat(a),local,key,null).statusCode()).isEqualTo(200);
  assertThat(pay(seat(b),vip,key,null).statusCode()).isEqualTo(409);
  assertThat(seatPayments.count()).isEqualTo(1);
 }
 @Test void rejectedPaymentRollsBackAndBrowserCannotProvideProof()throws Exception{
  long id=create(local,TypeTrajet.PUBLIC);long reservation=booking(id,local);UUID key=UUID.randomUUID();
  when(gateway.validate(eq(key),any())).thenThrow(new BusinessRuleException("PAYMENT_REFUSED"));
  assertThat(pay(seat(reservation),local,key,Map.of("accepted",true,"montant",20)).statusCode()).isEqualTo(409);
  assertThat(seatPayments.count()).isZero();
  assertThat(reservations.findById(reservation).orElseThrow().getStatut()).isEqualTo(StatutReservation.EN_ATTENTE);
  when(gateway.validate(eq(key),any())).thenReturn(PaymentGateway.Mode.SIMULATION);
  assertThat(pay(seat(reservation),local,key,null).statusCode()).isEqualTo(200);
 }
 @Test void unavailableProviderIs503WithoutConfirmation()throws Exception{
  long id=create(local,TypeTrajet.PUBLIC);long reservation=booking(id,local);
  when(gateway.validate(any(),any())).thenThrow(new PaymentUnavailableException());
  assertThat(pay(seat(reservation),local,UUID.randomUUID(),null).statusCode()).isEqualTo(503);
  assertThat(seatPayments.count()).isZero();
 }
 @Test void twoConcurrentPayersForLastPlaceNeverExceedFour()throws Exception{
  long id=create(local,TypeTrajet.PUBLIC);
  var a=account("O970001",CategorieClient.OCCASIONNEL,null);
  var b=account("O970002",CategorieClient.OCCASIONNEL,null);
  long organizer=booking(id,local),v=reserve(id,vip),o=reserve(id,occasional),ra=reserve(id,a),rb=reserve(id,b);
  for(var pair:List.of(Map.entry(local,organizer),Map.entry(vip,v),Map.entry(occasional,o)))
   assertThat(pay(seat(pair.getValue()),pair.getKey(),UUID.randomUUID(),null).statusCode()).isEqualTo(200);
  try(var pool=Executors.newFixedThreadPool(2)){
   var go=new CountDownLatch(1);
   var x=pool.submit(()->{go.await();return pay(seat(ra),a,UUID.randomUUID(),null).statusCode();});
   var y=pool.submit(()->{go.await();return pay(seat(rb),b,UUID.randomUUID(),null).statusCode();});
   go.countDown();
   assertThat(List.of(x.get(30,TimeUnit.SECONDS),y.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
  }
  assertThat(reservations.countByTrajetIdAndStatut(id,StatutReservation.CONFIRMEE)).isEqualTo(4);
  assertThat(seatPayments.count()).isEqualTo(4);
  assertThat(trips.findById(id).orElseThrow().getStatut()).isEqualTo(StatutTrajet.COMPLET);
  assertThat(call("POST","/api/trajets/"+id+"/reservations",ungrantedVip,null).statusCode()).isEqualTo(409);
 }
 @Test void concurrentReplayOfSamePaymentIsOneOperation()throws Exception{
  long id=create(local,TypeTrajet.PUBLIC);long reservation=booking(id,local);UUID key=UUID.randomUUID();
  try(var pool=Executors.newFixedThreadPool(2)){
   var go=new CountDownLatch(1);
   Callable<HttpResponse<String>> task=()->{go.await();return pay(seat(reservation),local,key,null);};
   var a=pool.submit(task);var b=pool.submit(task);go.countDown();
   var x=a.get(30,TimeUnit.SECONDS);var y=b.get(30,TimeUnit.SECONDS);
   assertThat(x.statusCode()).isEqualTo(200);assertThat(y.statusCode()).isEqualTo(200);
   assertThat(read(x).path("paiementId").asLong()).isEqualTo(read(y).path("paiementId").asLong());
  }
  assertThat(seatPayments.count()).isEqualTo(1);
 }
 @ParameterizedTest @ValueSource(strings={"anonymous","admin","other","inactiveAccount","inactiveClient"})
 void paymentRequiresActiveOwner(String kind)throws Exception{
  long id=create(local,TypeTrajet.PUBLIC);long reservation=booking(id,local);
  Compte actor=switch(kind){case "anonymous"->null;case "admin"->global;case "other"->vip;default->local;};
  if(kind.equals("inactiveAccount"))jdbc.update("update compte set actif=false where id=?",local.getId());
  if(kind.equals("inactiveClient"))jdbc.update("update client set actif=false where id=?",local.getClient().getId());
  int expected=switch(kind){case "anonymous","inactiveAccount","inactiveClient"->401;case "admin"->403;default->404;};
  assertThat(pay(seat(reservation),actor,UUID.randomUUID(),null).statusCode()).isEqualTo(expected);
  assertThat(seatPayments.count()).isZero();
 }
 @ParameterizedTest @ValueSource(strings={"ANNULE","COMPLET","unavailable","zoneInactive","grantRevoked","debt"})
 void allRulesAreRecheckedAtPayment(String kind)throws Exception{
  long id=create(local,TypeTrajet.PUBLIC);long reservation=reserve(id,vip);
  switch(kind){
   case "ANNULE","COMPLET"->jdbc.update("update trajet set statut=? where id=?",kind,id);
   case "unavailable"->{
    // V15 refuse désormais cette incohérence SQL ; la revalidation reste défensive.
    assertThatThrownBy(()->jdbc.update("insert into indisponibilite(vehicule_id,debut,fin) values(?,?,?)",
     vehicle.getId(),Timestamp.from(NOW.plusSeconds(172800)),Timestamp.from(NOW.plusSeconds(176400))))
     .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    doThrow(new BusinessRuleException("RESOURCE_UNAVAILABLE")).when(availability).requireAvailable(any(),any(),any());
   }
   case "zoneInactive"->jdbc.update("update zone set actif=false where id=?",home.getId());
   case "grantRevoked"->tx.executeWithoutResult(s->grants.revoke(vip.getClient().getId(),home.getId()));
   case "debt"->{
    var input=new HashMap<>(request(NOW.plusSeconds(259200),NOW.plusSeconds(262800),TypeTrajet.PUBLIC));
    input.put("vehiculeId",secondVehicle.getId());
    long debtTrip=read(call("POST","/api/trajets",vip,input)).path("trajetId").asLong();
    jdbc.update("insert into dette_trajet(trajet_id,client_id) values(?,?)",debtTrip,vip.getClient().getId());
   }
  }
  assertThat(pay(seat(reservation),vip,UUID.randomUUID(),null).statusCode())
   .isEqualTo(kind.equals("zoneInactive")||kind.equals("grantRevoked")?403:409);
  assertThat(seatPayments.count()).isZero();
 }
 @Test void missingKeyAndInvalidAmountsAre400()throws Exception{
  long id=create(local,TypeTrajet.PUBLIC);
  assertThat(pay(seat(booking(id,local)),local,null,null).statusCode()).isEqualTo(400);
  long debtId=generateDebtOnSecondVehicle();
  for(String amount:List.of("0","-1","80.01","1.001"))
   assertThat(pay(debt(debtId),local,UUID.randomUUID(),Map.of("montant",new BigDecimal(amount))).statusCode()).isEqualTo(400);
 }
 long generateDebtOnSecondVehicle()throws Exception{
  var input=new HashMap<>(request(NOW.plusSeconds(172800),NOW.plusSeconds(176400),TypeTrajet.PRIVE));
  input.put("vehiculeId",secondVehicle.getId());
  assertThat(call("POST","/api/trajets",local,input).statusCode()).isEqualTo(201);
  clock.set(deadline());assertThat(j1.catchUp()).isEqualTo(1);
  return debts.findAll().getFirst().getId();
 }
 @Test void j1DeadlineIsBrussels0015WithCatchUpAndIdempotence()throws Exception{
  long id=create(local,TypeTrajet.PRIVE);
  clock.set(deadline().minusSeconds(1));assertThat(j1.catchUp()).isZero();
  clock.set(deadline());assertThat(j1.catchUp()).isEqualTo(1);assertThat(j1.catchUp()).isZero();
  var trip=trips.findById(id).orElseThrow();
  assertThat(trip.getType()).isEqualTo(TypeTrajet.PUBLIC);assertThat(trip.getTraiteJ1Le()).isEqualTo(deadline());
  var debt=debts.findAll().getFirst();
  assertThat(debt.getMontant()).isEqualByComparingTo("80.00");
  assertThat(debt.getClient().getId()).isEqualTo(local.getClient().getId());
  assertThat(debt.getStatut()).isEqualTo("ACTIVE");
  clock.set(NOW.plusSeconds(86400*365L));assertThat(debts.findById(debt.getId()).orElseThrow().getStatut()).isEqualTo("ACTIVE");
 }
 @Test void concurrentJ1RunsProduceOneDebt()throws Exception{
  create(local,TypeTrajet.PRIVE);clock.set(deadline().plusSeconds(86400*3L));
  try(var pool=Executors.newFixedThreadPool(2)){
   var go=new CountDownLatch(1);
   Callable<Integer> job=()->{go.await();return j1.catchUp();};
   var a=pool.submit(job);var b=pool.submit(job);go.countDown();
   assertThat(a.get(30,TimeUnit.SECONDS)+b.get(30,TimeUnit.SECONDS)).isEqualTo(1);
  }
  assertThat(debts.count()).isEqualTo(1);
 }
 @Test void completeAndCancelledPrivateTripsDoNotGenerateDebt()throws Exception{
  long id=create(local,TypeTrajet.PRIVE);
  var more=account("O970003",CategorieClient.OCCASIONNEL,null);
  for(var actor:List.of(vip,occasional,more))
   assertThat(call("POST","/api/trajets/"+id+"/participants",local,Map.of("clientId",actor.getClient().getId())).statusCode()).isEqualTo(201);
  for(var actor:List.of(local,vip,occasional,more))
   assertThat(pay(seat(booking(id,actor)),actor,UUID.randomUUID(),null).statusCode()).isEqualTo(200);
  clock.set(deadline());assertThat(j1.catchUp()).isZero();assertThat(debts.count()).isZero();
 }

 @Test void cancelledPrivateTripNeverGeneratesDebt()throws Exception{
  long id=create(local,TypeTrajet.PRIVE);
  jdbc.update("update trajet set statut='ANNULE' where id=?",id);
  clock.set(deadline());assertThat(j1.catchUp()).isZero();assertThat(debts.count()).isZero();
 }
 @Test void j1FailureRollsBackConversionAndCanBeRetried()throws Exception{
  long id=create(local,TypeTrajet.PRIVE);clock.set(deadline());
  jdbc.execute("create function reject_test_debt() returns trigger language plpgsql as $$ begin raise exception 'test rollback' using errcode='23514'; end $$");
  jdbc.execute("create trigger reject_test_debt before insert on dette_trajet for each row execute function reject_test_debt()");
  try{
   assertThat(j1.catchUp()).isZero();
   assertThat(trips.findById(id).orElseThrow().getType()).isEqualTo(TypeTrajet.PRIVE);
   assertThat(debts.count()).isZero();
  }finally{
   jdbc.execute("drop trigger reject_test_debt on dette_trajet");jdbc.execute("drop function reject_test_debt()");
  }
  assertThat(j1.catchUp()).isEqualTo(1);
 }
 @Test void concurrentFinalPaymentAndJ1PreserveOneConsistentOutcome()throws Exception{
  long id=create(local,TypeTrajet.PRIVE);
  var more=account("O970004",CategorieClient.OCCASIONNEL,null);
  for(var actor:List.of(vip,occasional,more)){
   assertThat(call("POST","/api/trajets/"+id+"/participants",local,Map.of("clientId",actor.getClient().getId())).statusCode()).isEqualTo(201);
   assertThat(pay(seat(booking(id,actor)),actor,UUID.randomUUID(),null).statusCode()).isEqualTo(200);
  }
  clock.set(deadline());long organizer=booking(id,local);
  try(var pool=Executors.newFixedThreadPool(2)){
   var go=new CountDownLatch(1);
   var payment=pool.submit(()->{go.await();return pay(seat(organizer),local,UUID.randomUUID(),null).statusCode();});
   var job=pool.submit(()->{go.await();return j1.catchUp();});go.countDown();
   int code=payment.get(30,TimeUnit.SECONDS),processed=job.get(30,TimeUnit.SECONDS);
   if(code==200){assertThat(processed).isZero();assertThat(debts.count()).isZero();}
   else {assertThat(code).isEqualTo(409);assertThat(processed).isEqualTo(1);assertThat(debts.count()).isEqualTo(1);}
  }
  assertThat(reservations.countByTrajetIdAndStatut(id,StatutReservation.CONFIRMEE)).isBetween(3L,4L);
 }
 @Test void migrationV11ToV12PreservesPendingBookingsAndRejectsUnverifiablePayments(){
  String schema="payment_upgrade";
  var before=org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("11").load();before.migrate();
  jdbc.update("insert into "+schema+".zone(id,nom) values(1,'Legacy')");
  jdbc.update("insert into "+schema+".vehicule(id,zone_id) values(1,1)");
  jdbc.update("insert into "+schema+".client(id,numero,categorie,actif,zone_id) values(1,'C970099','LOCAL',true,1)");
  jdbc.update("insert into "+schema+".trajet(id,zone_id,vehicule_id,organisateur_id,depart,arrivee,type) values(1,1,1,1,?,?,'PUBLIC')",
   Timestamp.from(NOW),Timestamp.from(NOW.plusSeconds(3600)));
  jdbc.update("insert into "+schema+".reservation(trajet_id,client_id,statut) values(1,1,'CONFIRMEE')");
  var upgrade=org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("12").load();
  assertThatThrownBy(upgrade::migrate).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
  assertThat(jdbc.queryForObject("select statut from "+schema+".reservation",String.class)).isEqualTo("CONFIRMEE");
  jdbc.update("update "+schema+".reservation set statut='EN_ATTENTE'");
  assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
  assertThat(upgrade.validateWithResult().validationSuccessful).isTrue();
  assertThat(jdbc.queryForObject("select statut from "+schema+".reservation",String.class)).isEqualTo("EN_ATTENTE");
  assertThat(jdbc.queryForObject("select count(*) from "+schema+".paiement_place",Integer.class)).isZero();
 }
 @Test void partialDebtPaymentsStayActiveAndFullPaymentRestoresEligibility()throws Exception{
  long id=generateDebt();UUID first=UUID.randomUUID();
  assertThat(pay(debt(id),vip,UUID.randomUUID(),Map.of("montant",20)).statusCode()).isEqualTo(404);
  assertThat(pay(debt(id),local,first,Map.of("montant",20)).statusCode()).isEqualTo(200);
  assertThat(pay(debt(id),local,first,Map.of("montant",20)).statusCode()).isEqualTo(200);
  assertThat(debts.findById(id).orElseThrow().getStatut()).isEqualTo("ACTIVE");
  assertThat(debtPayments.count()).isEqualTo(1);
  var input=request(NOW.plusSeconds(345600),NOW.plusSeconds(349200),TypeTrajet.PUBLIC);
  assertThat(call("POST","/api/trajets",local,input).statusCode()).isEqualTo(409);
  assertThat(pay(debt(id),local,UUID.randomUUID(),Map.of("montant",60)).statusCode()).isEqualTo(200);
  assertThat(debts.findById(id).orElseThrow().getStatut()).isEqualTo("REGULARISEE");
  assertThat(debts.findById(id).orElseThrow().getRegle()).isEqualByComparingTo("80.00");
  assertThat(call("POST","/api/trajets",local,input).statusCode()).isEqualTo(201);
  assertThat(seatPayments.count()).isZero();
  assertThat(read(call("GET","/api/dettes/me",vip,null)).path("totalElements").asLong()).isZero();
  assertThat(read(call("GET","/api/dettes/me",local,null)).path("totalElements").asLong()).isEqualTo(1);
 }
 @Test void debtOverpaymentAndConflictingReplayDoNotChangeBalance()throws Exception{
  long id=generateDebt();UUID key=UUID.randomUUID();
  assertThat(pay(debt(id),local,key,Map.of("montant",20)).statusCode()).isEqualTo(200);
  assertThat(pay(debt(id),local,key,Map.of("montant",40)).statusCode()).isEqualTo(409);
  assertThat(pay(debt(id),local,UUID.randomUUID(),Map.of("montant",80)).statusCode()).isEqualTo(409);
  assertThat(debts.findById(id).orElseThrow().getRegle()).isEqualByComparingTo("20.00");
 }
 @Test void concurrentDebtPaymentsCannotOverpay()throws Exception{
  long id=generateDebt();
  try(var pool=Executors.newFixedThreadPool(2)){
   var go=new CountDownLatch(1);
   Callable<Integer> job=()->{go.await();return pay(debt(id),local,UUID.randomUUID(),Map.of("montant",60)).statusCode();};
   var a=pool.submit(job);var b=pool.submit(job);go.countDown();
   assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
  }
  assertThat(debts.findById(id).orElseThrow().getRegle()).isEqualByComparingTo("60.00");
  assertThat(debtPayments.count()).isEqualTo(1);
 }
 @Test void failedDebtValidationDoesNotCreditBalance()throws Exception{
  long id=generateDebt();when(gateway.validate(any(),any())).thenThrow(new BusinessRuleException("PAYMENT_REFUSED"));
  assertThat(pay(debt(id),local,UUID.randomUUID(),Map.of("montant",80)).statusCode()).isEqualTo(409);
  assertThat(debtPayments.count()).isZero();
  assertThat(debts.findById(id).orElseThrow().getRegle()).isEqualByComparingTo("0.00");
 }
 @Test void postgresRejectsConfirmationWithoutPaymentAndFalseDebtBalance()throws Exception{
  long id=create(local,TypeTrajet.PUBLIC);
  assertThatThrownBy(()->jdbc.update("update reservation set statut='CONFIRMEE' where id=?",booking(id,local)))
   .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  long debtId=generateDebtOnSecondVehicle();
  assertThatThrownBy(()->jdbc.update("update dette_trajet set regle=20 where id=?",debtId))
   .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }

 @ParameterizedTest @ValueSource(strings={"LOCAL","VIP","OCCASIONNEL"})
 void clientCatalogUsesAuthorizedZonesBeforePaginationAndCounts(String category)throws Exception{
  Compte actor=switch(category){case "VIP"->vip;case "OCCASIONNEL"->occasional;default->local;};
  var zonePage=read(call("GET","/api/zones?size=1",actor,null));
  assertThat(zonePage.path("totalElements").asLong()).isEqualTo(1);
  assertThat(zonePage.path("content").get(0).path("zoneId").asLong()).isEqualTo(home.getId());
  var fleetPage=read(call("GET","/api/vehicules?size=1",actor,null));
  assertThat(fleetPage.path("totalElements").asLong()).isEqualTo(2);
  assertThat(read(call("GET","/api/vehicules?zoneId="+away.getId(),actor,null)).path("totalElements").asLong()).isZero();
 }
 @Test void clientCatalogCannotBroadenScopeOrGrantMutation()throws Exception{
  assertThat(read(call("GET","/api/zones",ungrantedVip,null)).path("totalElements").asLong()).isZero();
  assertThat(call("GET","/api/zones",null,null).statusCode()).isEqualTo(401);
  assertThat(call("GET","/api/vehicules",global,null).statusCode()).isEqualTo(403);
  assertThat(call("POST","/api/vehicules",local,Map.of("zoneId",home.getId())).statusCode()).isEqualTo(403);
  jdbc.update("update zone set actif=false where id=?",home.getId());
  assertThat(read(call("GET","/api/zones",local,null)).path("totalElements").asLong()).isZero();
 }
 @Test void schedulerCronIsExplicitAndSwaggerLabelsSimulation()throws Exception{
  var cron=J1Scheduler.class.getMethod("process").getAnnotation(Scheduled.class);
  assertThat(cron.cron()).isEqualTo("0 15 0 * * *");assertThat(cron.zone()).isEqualTo("Europe/Brussels");
  var response=call("GET","/v3/api-docs",null,null);
  assertThat(response.body()).contains("SIMULATION");
  assertThat(read(response).path("paths").path("/api/dettes/{id}/paiements").has("post")).isTrue();
 }

 private static final String ANGULAR_ORIGIN="http://localhost:4200";
 HttpResponse<String> preflight(String path,String origin,String headers)throws Exception{
  return http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
   .header("Origin",origin).header("Access-Control-Request-Method","POST")
   .header("Access-Control-Request-Headers",headers)
   .method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
 }
 String paymentPath(String kind,long id){return kind.equals("PLACE")?seat(id):debt(id);}
 long paymentTarget(String kind)throws Exception{
  return kind.equals("PLACE")?booking(create(local,TypeTrajet.PUBLIC),local):generateDebt();
 }
 Object paymentBody(String kind){return kind.equals("PLACE")?null:Map.of("montant",20);}

 @ParameterizedTest @ValueSource(strings={"PLACE","DETTE"})
 void paymentPreflightExplicitlyAllowsTheIdempotencyHeader(String kind)throws Exception{
  var response=preflight(paymentPath(kind,1),ANGULAR_ORIGIN,"authorization,content-type,idempotency-key");
  assertThat(response.statusCode()).isEqualTo(200);
  assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(ANGULAR_ORIGIN);
  assertThat(response.headers().firstValue("Access-Control-Allow-Headers").orElseThrow().toLowerCase(Locale.ROOT))
   .contains("authorization","content-type","idempotency-key");
  assertThat(response.headers().firstValue("Access-Control-Allow-Methods").orElseThrow()).contains("POST");
  assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
 }
 @ParameterizedTest @ValueSource(strings={"PLACE","DETTE"})
 void authenticatedAngularPaymentsAndReplaysKeepTheExistingContract(String kind)throws Exception{
  long id=paymentTarget(kind);String path=paymentPath(kind,id);UUID key=UUID.randomUUID();
  var first=pay(path,local,key,paymentBody(kind),ANGULAR_ORIGIN);
  var replay=pay(path,local,key,paymentBody(kind),ANGULAR_ORIGIN);
  assertThat(first.statusCode()).isEqualTo(200);assertThat(replay.statusCode()).isEqualTo(200);
  assertThat(first.headers().firstValue("Access-Control-Allow-Origin")).contains(ANGULAR_ORIGIN);
  assertThat(read(first).path("paiementId").asLong()).isEqualTo(read(replay).path("paiementId").asLong());
  assertThat(read(first).path("montant").decimalValue()).isEqualByComparingTo("20.00");
  verify(gateway,times(1)).validate(eq(key),any());
 }
 @ParameterizedTest @ValueSource(strings={"PLACE","DETTE"})
 void allowingCorsDoesNotRemoveAuthenticationOrRoleChecks(String kind)throws Exception{
  long id=paymentTarget(kind);String path=paymentPath(kind,id);Object body=paymentBody(kind);
  assertThat(pay(path,null,UUID.randomUUID(),body,ANGULAR_ORIGIN).statusCode()).isEqualTo(401);
  assertThat(pay(path,global,UUID.randomUUID(),body,ANGULAR_ORIGIN).statusCode()).isEqualTo(403);
  var invalid=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
   .header("Origin",ANGULAR_ORIGIN).header("Authorization","Bearer invalid.jwt.token")
   .header("Content-Type","application/json").header("Idempotency-Key",UUID.randomUUID().toString())
   .POST(HttpRequest.BodyPublishers.ofString(body==null?"":json.writeValueAsString(body))).build();
  assertThat(http.send(invalid,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
  verify(gateway,never()).validate(any(),any());
 }
 @ParameterizedTest @ValueSource(strings={"PLACE","DETTE"})
 void unauthorizedOriginsCannotPreflightOrSubmitAuthenticatedPayments(String kind)throws Exception{
  long id=paymentTarget(kind);String path=paymentPath(kind,id);String denied="http://untrusted.test";
  var options=preflight(path,denied,"authorization,content-type,idempotency-key");
  var post=pay(path,local,UUID.randomUUID(),paymentBody(kind),denied);
  assertThat(options.statusCode()).isEqualTo(403);assertThat(post.statusCode()).isEqualTo(403);
  assertThat(options.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
  assertThat(post.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
  verify(gateway,never()).validate(any(),any());
  assertThat(seatPayments.count()).isZero();assertThat(debtPayments.count()).isZero();
 }
 @ParameterizedTest @ValueSource(strings={"PLACE","DETTE"})
 void paymentCorsPolicyStillRejectsUnlistedHeaders(String kind)throws Exception{
  var response=preflight(paymentPath(kind,1),ANGULAR_ORIGIN,"X-Privilege-Override");
  assertThat(response.statusCode()).isEqualTo(403);
  assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
 }
}
