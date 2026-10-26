package com.plateforme.covoiturage;

import com.plateforme.covoiturage.dto.request.ReferencePageRequest;
import com.plateforme.covoiturage.entity.*;
import com.plateforme.covoiturage.enums.*;
import com.plateforme.covoiturage.repository.*;
import com.plateforme.covoiturage.security.ComptePrincipal;
import com.plateforme.covoiturage.service.*;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.*;
import javax.sql.DataSource;
import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class ReferentielIntegrationTests {
    static final Instant START=Instant.parse("2026-10-25T08:00:00Z");
    static final Instant END=START.plusSeconds(3600);
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired ZoneRepository zones;
    @Autowired VehiculeRepository vehicles;
    @Autowired IndisponibiliteRepository periods;
    @Autowired CompteRepository accounts;
    @Autowired ClientRepository clients;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired AuthService auth;
    @Autowired ReferentielService service;
    @Autowired ZoneAccessPolicy access;
    @Autowired AvailabilityService availability;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired EntityManagerFactory emf;
    final HttpClient http=HttpClient.newHttpClient();
    Zone home,away,empty;
    Vehicule homeVehicle,awayVehicle;
    Indisponibilite homeZonePeriod,homeVehiclePeriod,awayZonePeriod,awayVehiclePeriod;
    Compte global,local,client;
    String hash;

    @BeforeEach void disposableFixturesOnly(){
        jdbc.update("delete from indisponibilite");jdbc.update("delete from invitation_outbox");
        jdbc.update("delete from invitation");jdbc.update("delete from compte");jdbc.update("delete from vehicule");
        jdbc.execute("truncate client_numero_reservation");jdbc.update("delete from client");jdbc.update("delete from zone");
        home=zones.saveAndFlush(new Zone("Zone A"));away=zones.saveAndFlush(new Zone("Zone B"));
        empty=zones.saveAndFlush(new Zone("Zone sans véhicule"));
        homeVehicle=vehicles.saveAndFlush(new Vehicule(home));vehicles.saveAndFlush(new Vehicule(home));
        awayVehicle=vehicles.saveAndFlush(new Vehicule(away));vehicles.saveAndFlush(new Vehicule(away));
        hash=passwords.encode(UUID.randomUUID().toString());
        global=accounts.saveAndFlush(new Compte("network@references.test",hash,true,RoleTechnique.ADMIN,null,PerimetreAdmin.GLOBAL,null));
        local=accounts.saveAndFlush(new Compte("area@references.test",hash,true,RoleTechnique.ADMIN,null,PerimetreAdmin.LOCAL,home));
        var person=clients.saveAndFlush(new Client("C990001",CategorieClient.LOCAL,true,home));
        client=accounts.saveAndFlush(new Compte(person.getNumero(),hash,true,RoleTechnique.CLIENT,person,null,null));
        homeZonePeriod=periods.saveAndFlush(Indisponibilite.pourZone(home,START,END));
        homeVehiclePeriod=periods.saveAndFlush(Indisponibilite.pourVehicule(homeVehicle,START,END));
        awayZonePeriod=periods.saveAndFlush(Indisponibilite.pourZone(away,START,END));
        awayVehiclePeriod=periods.saveAndFlush(Indisponibilite.pourVehicule(awayVehicle,START,END));
    }
    @AfterEach void releaseNewForeignKeysForOtherCachedTestClasses(){jdbc.update("delete from autorisation_zone_vip");jdbc.update("delete from indisponibilite");}

    HttpResponse<String> get(String path,Compte actor)throws Exception{
        return bearer(path,actor==null?null:tokens.issue(actor.getId()).accessToken());
    }
    HttpResponse<String> bearer(String path,String token)throws Exception{
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path));
        if(token!=null)request.header("Authorization","Bearer "+token);
        return http.send(request.GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode read(HttpResponse<String> response){return json.readTree(response.body());}
    String api(String resource){return "/api/admin/"+resource;}
    long awayId(String resource){return switch(resource){case "zones"->away.getId();case "vehicules"->awayVehicle.getId();default->awayZonePeriod.getId();};}
    long homeId(String resource){return switch(resource){case "zones"->home.getId();case "vehicules"->homeVehicle.getId();default->homeZonePeriod.getId();};}
    void safe(String body){
        assertThat(body.contains("password")||body.contains("hash")||body.contains("accessToken")
                ||body.contains("invitationEmail")||body.contains("empreinte")||body.contains("contenu_chiffre")).isFalse();
    }

    @ParameterizedTest @ValueSource(strings={"zones","vehicules","indisponibilites"})
    void globalReadsNetworkAndPaginatesWithCount(String resource)throws Exception{
        var response=get(api(resource)+"?size=1",global);
        assertThat(response.statusCode()).isEqualTo(200);
        var page=read(response);
        assertThat(page.path("content").size()).isEqualTo(1);
        assertThat(page.path("totalElements").asLong()).isEqualTo(resource.equals("zones")?3:4);
        assertThat(page.path("totalPages").asInt()).isEqualTo(resource.equals("zones")?3:4);
        assertThat(page.path("page").asInt()).isZero();
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        safe(response.body());
    }
    @ParameterizedTest @ValueSource(strings={"zones","vehicules","indisponibilites"})
    void localScopeIsAppliedBeforePaginationAndCount(String resource)throws Exception{
        var response=get(api(resource)+"?size=1",local);
        assertThat(response.statusCode()).isEqualTo(200);
        long expected=resource.equals("zones")?1:2;
        assertThat(read(response).path("totalElements").asLong()).isEqualTo(expected);
        assertThat(read(response).path("content").size()).isEqualTo(1);
        safe(response.body());
    }
    @Test void vehicleAndDirectZonePeriodsAreBothVisibleInsideLocalScope()throws Exception{
        var response=get(api("indisponibilites"),local);assertThat(response.statusCode()).isEqualTo(200);
        var ids=new ArrayList<Long>();read(response).path("content").forEach(n->ids.add(n.path("indisponibiliteId").asLong()));
        assertThat(ids).containsExactly(homeZonePeriod.getId(),homeVehiclePeriod.getId());
        var direct=read(get(api("indisponibilites")+"/"+homeZonePeriod.getId(),local));
        var vehicle=read(get(api("indisponibilites")+"/"+homeVehiclePeriod.getId(),local));
        assertThat(direct.path("vehiculeId").isNull()).isTrue();
        assertThat(vehicle.path("zoneId").isNull()).isTrue();
        assertThat(vehicle.path("vehiculeId").asLong()).isEqualTo(homeVehicle.getId());
        assertThat(vehicle.path("debut").asText()).isEqualTo(START.toString());
    }
    @ParameterizedTest @ValueSource(strings={"zones","vehicules","indisponibilites"})
    void detailsAreScopedAndAbsentAndInvisibleHaveSame404(String resource)throws Exception{
        assertThat(get(api(resource)+"/"+homeId(resource),local).statusCode()).isEqualTo(200);
        assertThat(get(api(resource)+"/"+awayId(resource),global).statusCode()).isEqualTo(200);
        var invisible=get(api(resource)+"/"+awayId(resource),local);
        var absent=get(api(resource)+"/"+Long.MAX_VALUE,local);
        assertThat(invisible.statusCode()).isEqualTo(404);assertThat(absent.statusCode()).isEqualTo(404);
        assertThat(read(invisible).path("message").asText()).isEqualTo(read(absent).path("message").asText());
        assertThat(read(invisible).path("code").asText()).isEqualTo("NOT_FOUND");
    }
    @ParameterizedTest @ValueSource(strings={"zones","vehicules","indisponibilites"})
    void anonymousClientAndInvalidJwtCannotReadAdministrativeReferences(String resource)throws Exception{
        assertThat(get(api(resource),null).statusCode()).isEqualTo(401);
        assertThat(get(api(resource),client).statusCode()).isEqualTo(403);
        assertThat(bearer(api(resource),"invalid-jwt").statusCode()).isEqualTo(401);
        assertThat(get(api(resource)+"/"+homeId(resource),null).statusCode()).isEqualTo(401);
        assertThat(get(api(resource)+"/"+homeId(resource),client).statusCode()).isEqualTo(403);
    }
    @Test void inactiveActorIsRejectedEvenWithPreviouslyIssuedJwt()throws Exception{
        String token=tokens.issue(local.getId()).accessToken();
        jdbc.update("update compte set actif=false where id=?",local.getId());
        assertThat(bearer(api("zones"),token).statusCode()).isEqualTo(401);
    }
    @ParameterizedTest @ValueSource(strings={"vehicules","indisponibilites"})
    void zoneFiltersCannotExpandScope(String resource)throws Exception{
        var globalFiltered=get(api(resource)+"?zoneId="+away.getId(),global);
        assertThat(globalFiltered.statusCode()).isEqualTo(200);
        assertThat(read(globalFiltered).path("totalElements").asLong()).isEqualTo(2);
        for(Long zoneId:List.of(away.getId(),Long.MAX_VALUE)){
            var response=get(api(resource)+"?zoneId="+zoneId,local);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(read(response).path("totalElements").asLong()).isZero();
            assertThat(read(response).path("content").size()).isZero();
        }
    }
    @Test void vehicleAndZonePeriodFiltersAreIntersections()throws Exception{
        String base=api("indisponibilites")+"?vehiculeId="+awayVehicle.getId();
        assertThat(read(get(base,global)).path("totalElements").asLong()).isEqualTo(1);
        assertThat(read(get(base,local)).path("totalElements").asLong()).isZero();
        assertThat(read(get(base+"&zoneId="+home.getId(),global)).path("totalElements").asLong()).isZero();
        assertThat(read(get(base+"&zoneId="+away.getId(),global)).path("totalElements").asLong()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"zones?page=-1","zones?size=0","vehicules?size=101",
            "indisponibilites?page=2147483647&size=100","vehicules?zoneId=0","indisponibilites?vehiculeId=-1",
            "zones?page=abc","zones/0"})
    void invalidParametersAre400WithoutDataDisclosure(String suffix)throws Exception{
        var response=get("/api/admin/"+suffix,global);
        assertThat(response.statusCode()).isEqualTo(400);safe(response.body());
    }
    @Test void paginationDefaultsAndStableIdOrderMatchExistingConventions()throws Exception{
        var first=get(api("vehicules"),global);
        assertThat(read(first).path("size").asInt()).isEqualTo(20);
        assertThat(read(first).path("page").asInt()).isZero();
        var a=get(api("vehicules")+"?size=1&page=0",global);
        var b=get(api("vehicules")+"?size=1&page=1",global);
        assertThat(read(a).path("content").get(0).path("vehiculeId").asLong())
                .isLessThan(read(b).path("content").get(0).path("vehiculeId").asLong());
        assertThat(read(get(api("zones")+"?page=9&size=1",global)).path("content").size()).isZero();
    }
    @Test void commonAuthorizationPolicyAlsoProtectsDirectServiceCalls(){
        var noAuth=UsernamePasswordAuthenticationToken.unauthenticated("unknown",null);
        assertThatThrownBy(()->service.zones(noAuth,new ReferencePageRequest(null,null))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.vehicules(null,new ReferencePageRequest(null,null),null)).isInstanceOf(AccessDeniedException.class);
    }
    @ParameterizedTest @ValueSource(strings={"vehicules","indisponibilites"})
    void pagesDoNotTriggerNPlusOne(String resource){
        var actor=UsernamePasswordAuthenticationToken.authenticated(
                new ComptePrincipal(auth.resolvePrincipal(global.getId().toString()).profile()),null,List.of());
        var statistics=emf.unwrap(SessionFactory.class).getStatistics();statistics.clear();
        if(resource.equals("vehicules"))service.vehicules(actor,new ReferencePageRequest(0,2),null);
        else service.indisponibilites(actor,new ReferencePageRequest(0,2),null,null);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(strings={"noTarget","bothTargets","missingZone","missingVehicle",
            "missingStart","sameDates","reversedDates"})
    void postgresEnforcesExclusiveTargetForeignKeysAndValidPeriod(String kind){
        Long zone=home.getId(),vehicle=null;
        Timestamp start=Timestamp.from(START),end=Timestamp.from(END);
        switch(kind){
            case "noTarget"->zone=null;
            case "bothTargets"->vehicle=homeVehicle.getId();
            case "missingZone"->zone=Long.MAX_VALUE;
            case "missingVehicle"->{zone=null;vehicle=Long.MAX_VALUE;}
            case "missingStart"->start=null;
            case "sameDates"->end=start;
            case "reversedDates"->end=Timestamp.from(START.minusSeconds(1));
        }
        Long zoneId=zone,vehicleId=vehicle;Timestamp begin=start,finish=end;
        assertThatThrownBy(()->jdbc.update("insert into indisponibilite(zone_id,vehicule_id,debut,fin) values(?,?,?,?)",
                zoneId,vehicleId,begin,finish)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(periods.count()).isEqualTo(4);
    }
    @ParameterizedTest @ValueSource(strings={"zone","vehicule"})
    void postgresPreservesResourcesReferencedByPeriods(String kind){
        var targetZone=zones.saveAndFlush(new Zone("Référence isolée"));
        if(kind.equals("zone")){
            periods.saveAndFlush(Indisponibilite.pourZone(targetZone,START,END));
            assertThatThrownBy(()->jdbc.update("delete from zone where id=?",targetZone.getId())).isInstanceOf(DataIntegrityViolationException.class);
        }else{
            var vehicle=vehicles.saveAndFlush(new Vehicule(targetZone));
            periods.saveAndFlush(Indisponibilite.pourVehicule(vehicle,START,END));
            assertThatThrownBy(()->jdbc.update("delete from vehicule where id=?",vehicle.getId())).isInstanceOf(DataIntegrityViolationException.class);
        }
    }
    @Test void v8ToV9PreservesExistingClientAccountAndVehicle(){
        String schema="references_upgrade";
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("8").load().migrate();
        jdbc.update("insert into "+schema+".zone(id,nom) values(9001,'Zone conservée')");
        jdbc.update("insert into "+schema+".vehicule(id,zone_id) values(9001,9001)");
        jdbc.update("insert into "+schema+".client(id,numero,categorie,actif,zone_id) values(9001,'C987654','LOCAL',false,9001)");
        jdbc.update("insert into "+schema+".compte(id,identifiant,password_hash,actif,role,client_id) values(9001,'C987654',?,false,'CLIENT',9001)",hash);
        var upgraded=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("9").load();
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from "+schema+".indisponibilite",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select c.identifiant='C987654' and c.password_hash=? and not c.actif and not cl.actif"
                +" and cl.zone_id=v.zone_id and c.etat_identifiants='INITIALISE' from "+schema+".compte c"
                +" join "+schema+".client cl on cl.id=c.client_id join "+schema+".vehicule v on v.id=9001",Boolean.class,hash)).isTrue();
    }

    HttpResponse<String> write(String method,String path,Compte actor,String body)throws Exception{
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
            .header("Content-Type","application/json");
        if(actor!=null)request.header("Authorization","Bearer "+tokens.issue(actor.getId()).accessToken());
        return http.send(request.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    String configuration(String timezone){
        return json.writeValueAsString(Map.of("nom","Zone configuree","fuseauHoraire",timezone,"ouverteOccasionnels",true));
    }
    @Test void globalCreatesUpdatesAndDeactivatesWithoutDeletingReferences()throws Exception{
        var created=write("POST",api("zones"),global,configuration("Europe/Brussels"));
        assertThat(created.statusCode()).isEqualTo(201);safe(created.body());
        long id=read(created).path("zoneId").asLong();
        assertThat(read(created).path("actif").asBoolean()).isTrue();
        assertThat(write("PUT",api("zones")+"/"+home.getId(),global,configuration("Europe/Paris")).statusCode()).isEqualTo(200);
        var deactivated=write("PATCH",api("zones")+"/"+home.getId()+"/statut",global,"{\"actif\":false}");
        assertThat(deactivated.statusCode()).isEqualTo(200);
        assertThat(read(deactivated).path("actif").asBoolean()).isFalse();
        assertThat(vehicles.findById(homeVehicle.getId())).isPresent();
        assertThat(clients.findById(client.getClient().getId())).isPresent();
        assertThat(zones.findById(id)).isPresent();
    }
    @ParameterizedTest @ValueSource(strings={"anonymous","local","client"})
    void zoneWritesRequireGlobal(String role)throws Exception{
        Compte actor=switch(role){case "local"->local;case "client"->client;default->null;};
        assertThat(write("POST",api("zones"),actor,configuration("Europe/Brussels")).statusCode())
            .isEqualTo(actor==null?401:403);
        assertThat(write("PATCH",api("zones")+"/"+home.getId()+"/statut",actor,"{\"actif\":false}").statusCode())
            .isEqualTo(actor==null?401:403);
    }
    @ParameterizedTest @ValueSource(strings={"", "Not/AZone", "UTC+99"})
    void invalidTimezoneIs400(String timezone)throws Exception{
        assertThat(write("POST",api("zones"),global,configuration(timezone)).statusCode()).isEqualTo(400);
    }
    @Test void missingZoneOnWriteIs404()throws Exception{
        assertThat(write("PUT",api("zones")+"/"+Long.MAX_VALUE,global,configuration("Europe/Brussels")).statusCode()).isEqualTo(404);
    }
    @Test void explicitVipGrantsCannotBeReplacedByCategoryAlone()throws Exception{
        var vip=clients.saveAndFlush(new Client("V990002",CategorieClient.VIP,true,null));
        assertThat(access.permits(vip,home)).isFalse();
        String endpoint=api("zones")+"/"+home.getId()+"/clients-vip/"+vip.getId();
        assertThat(write("PUT",endpoint,local,"").statusCode()).isEqualTo(403);
        assertThat(write("PUT",endpoint,global,"").statusCode()).isEqualTo(204);
        assertThat(write("PUT",endpoint,global,"").statusCode()).isEqualTo(204);
        assertThat(access.permits(vip,home)).isTrue();assertThat(access.permits(vip,away)).isFalse();
        assertThat(write("DELETE",endpoint,global,"").statusCode()).isEqualTo(204);
        assertThat(access.permits(vip,home)).isFalse();
        assertThat(write("PUT",api("zones")+"/"+home.getId()+"/clients-vip/"+client.getClient().getId(),global,"").statusCode()).isEqualTo(409);
    }
    @Test void localAndOccasionalAccessFollowDatabaseConfiguration()throws Exception{
        var person=client.getClient();
        assertThat(access.permits(person,home)).isTrue();assertThat(access.permits(person,away)).isFalse();
        var occasional=clients.saveAndFlush(new Client("O990003",CategorieClient.OCCASIONNEL,true,null));
        assertThat(access.permits(occasional,home)).isFalse();
        write("PUT",api("zones")+"/"+home.getId(),global,configuration("Europe/Brussels"));
        var fresh=zones.findById(home.getId()).orElseThrow();
        assertThat(access.permits(occasional,fresh)).isTrue();
        write("PATCH",api("zones")+"/"+home.getId()+"/statut",global,"{\"actif\":false}");
        assertThat(access.permits(person,zones.findById(home.getId()).orElseThrow())).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"inside","leftOverlap","rightOverlap"})
    void zoneOrVehicleUnavailabilityBlocksOverlaps(String kind){
        Instant start=switch(kind){case "leftOverlap"->START.minusSeconds(1800);case "rightOverlap"->END.minusSeconds(1);default->START;};
        assertThatThrownBy(()->availability.requireAvailable(homeVehicle,start,start.plusSeconds(3600)))
            .isInstanceOf(com.plateforme.covoiturage.exception.BusinessRuleException.class);
    }
    @Test void availabilityUsesHalfOpenIntervalsAndRejectsInvalidDates(){
        availability.requireAvailable(homeVehicle,END,END.plusSeconds(3600));
        availability.requireAvailable(homeVehicle,START.minusSeconds(3600),START);
        assertThatThrownBy(()->availability.requireAvailable(homeVehicle,END,START))
            .isInstanceOf(com.plateforme.covoiturage.exception.BusinessRuleException.class);
    }
    @Test void postgresRejectsGrantToNonVipAndPreservesZoneOnDeactivation()throws Exception{
        assertThatThrownBy(()->jdbc.update("insert into autorisation_zone_vip(client_id,zone_id) values(?,?)",
            client.getClient().getId(),home.getId())).isInstanceOf(DataIntegrityViolationException.class);
        write("PATCH",api("zones")+"/"+home.getId()+"/statut",global,"{\"actif\":false}");
        assertThat(jdbc.queryForObject("select count(*) from vehicule where zone_id=?",Integer.class,home.getId())).isEqualTo(2);
    }
    @Test void swaggerExposesExactlyValidatedReferenceOperations()throws Exception{
        var response=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/v3/api-docs")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var paths=read(response).path("paths");
        for(String resource:List.of("zones","vehicules","indisponibilites")){
            for(String path:List.of(api(resource),api(resource)+"/{id}")){
                assertThat(paths.path(path).has("get")).isTrue();
                for(String verb:List.of("post","put","patch","delete")){
                    boolean collection=path.equals(api(resource));
                    boolean allowed=(collection&&verb.equals("post"))
                        || (!collection&&verb.equals("put")&&!resource.equals("vehicules"))
                        || (!collection&&verb.equals("delete")&&resource.equals("indisponibilites"));
                    assertThat(paths.path(path).has(verb)).isEqualTo(allowed);
                }
            }
        }
        assertThat(paths.path(api("vehicules")+"/{id}/statut").has("patch")).isTrue();
        assertThat(paths.path(api("zones")+"/{id}/statut").has("patch")).isTrue();
    }
}
