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

@SpringBootTest(classes=CovoiturageApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class,JourneyIntegrationSupport.TimeConfiguration.class})
@ActiveProfiles("dev")
abstract class JourneyIntegrationSupport {
 static final Instant NOW=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
 @TestConfiguration(proxyBeanMethods=false) static class TimeConfiguration {
  @Bean @Primary ControlledClock journeyClock(){return new ControlledClock();}
 }
 static class ControlledClock extends Clock {
  final AtomicReference<Instant> value=new AtomicReference<>(NOW);
  void set(Instant now){value.set(now);}
  public ZoneId getZone(){return ZoneOffset.UTC;}
  public Clock withZone(ZoneId zone){return Clock.fixed(instant(),zone);}
  public Instant instant(){return value.get();}
 }
 @Autowired ControlledClock clock;
 @Autowired ZoneRepository zones;
 @Autowired VehiculeRepository vehicles;
 @Autowired ClientRepository clients;
 @Autowired CompteRepository accounts;
 @Autowired TrajetRepository trips;
 @Autowired ReservationRepository reservations;
 @Autowired PasswordEncoder passwords;
 @Autowired JwtEncoder encoder;
 @Autowired JwtProperties jwtProperties;
 @Autowired JdbcTemplate jdbc;
 @Autowired ObjectMapper json;
 @Autowired TransactionTemplate tx;
 @Autowired DataSource dataSource;
 @Autowired BookingPolicy policy;
 @Autowired ZoneAccessRepository grants;
 @LocalServerPort int port;
 final HttpClient http=HttpClient.newHttpClient();
 Zone home,away;
 Vehicule vehicle,secondVehicle;
 Compte local,otherLocal,vip,ungrantedVip,occasional,global;
 String hash;
 @BeforeEach void disposableFixtures(){
  clock.set(NOW);
  cleanBusiness();
  jdbc.update("delete from invitation_outbox");jdbc.update("delete from invitation");
  jdbc.update("delete from compte");jdbc.update("delete from vehicule");
  jdbc.execute("truncate client_numero_reservation");jdbc.update("delete from client");jdbc.update("delete from zone");
  home=new Zone("Home");home.configure("Home","Europe/Brussels",true);home=zones.saveAndFlush(home);
  away=new Zone("Away");away.configure("Away","Europe/Brussels",false);away=zones.saveAndFlush(away);
  vehicle=vehicles.saveAndFlush(new Vehicule(home));secondVehicle=vehicles.saveAndFlush(new Vehicule(home));
  hash=passwords.encode(UUID.randomUUID().toString());
  local=account("C980001",CategorieClient.LOCAL,home);
  otherLocal=account("C980002",CategorieClient.LOCAL,away);
  vip=account("V980001",CategorieClient.VIP,null);
  ungrantedVip=account("V980002",CategorieClient.VIP,null);
  occasional=account("O980001",CategorieClient.OCCASIONNEL,null);
  tx.executeWithoutResult(s->grants.grant(vip.getClient().getId(),home.getId()));
  global=accounts.saveAndFlush(new Compte("journeys@tests.test",hash,true,RoleTechnique.ADMIN,null,PerimetreAdmin.GLOBAL,null));
 }
 void cleanBusiness(){
  jdbc.update("delete from dette_trajet");jdbc.update("delete from reservation");jdbc.update("delete from trajet");
  jdbc.update("delete from autorisation_zone_vip");jdbc.update("delete from indisponibilite");
 }
 @AfterEach void releaseForeignKeys(){cleanBusiness();}
 Compte account(String number,CategorieClient category,Zone zone){
  var client=clients.saveAndFlush(new Client(number,category,true,zone));
  return accounts.saveAndFlush(new Compte(number,hash,true,RoleTechnique.CLIENT,client,null,null));
 }
 HttpResponse<String> call(String method,String path,Compte actor,Object body)throws Exception{
  var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
  if(actor!=null)request.header("Authorization","Bearer "+new TokenService(encoder,jwtProperties,Clock.systemUTC()).issue(actor.getId()).accessToken());
  String payload=body==null?"":json.writeValueAsString(body);
  return http.send(request.method(method,HttpRequest.BodyPublishers.ofString(payload)).build(),HttpResponse.BodyHandlers.ofString());
 }
 JsonNode read(HttpResponse<String> response){return json.readTree(response.body());}
 Map<String,Object> request(Instant start,Instant end,TypeTrajet type){
  return Map.of("zoneId",home.getId(),"vehiculeId",vehicle.getId(),"depart",start.toString(),"arrivee",end.toString(),"type",type,
   "origine","Gare de Bruxelles-Midi","destination","Gare de Liège-Guillemins");
 }
 long create(Compte actor,TypeTrajet type)throws Exception{
  var result=call("POST","/api/trajets",actor,request(NOW.plusSeconds(172800),NOW.plusSeconds(176400),type));
  assertThat(result.statusCode()).isEqualTo(201);return read(result).path("trajetId").asLong();
 }
}
