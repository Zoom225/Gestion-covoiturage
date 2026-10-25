package com.plateforme.covoiturage;

import com.plateforme.covoiturage.dto.request.*;
import com.plateforme.covoiturage.entity.*;
import com.plateforme.covoiturage.enums.*;
import com.plateforme.covoiturage.exception.InvitationConflictException;
import com.plateforme.covoiturage.repository.*;
import com.plateforme.covoiturage.security.ComptePrincipal;
import com.plateforme.covoiturage.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.*;
import javax.sql.DataSource;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class,InvitationOutboxIntegrationTests.Fakes.class})
@ActiveProfiles("dev")
class AccountWorkflowHttpIntegrationTests {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired CompteRepository comptes;
    @Autowired ClientRepository clients;
    @Autowired ZoneRepository zones;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired AuthService auth;
    @Autowired CompteService service;
    @Autowired InvitationService invitations;
    @Autowired InvitationDeliveryService delivery;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired TransactionTemplate transaction;
    @Autowired InvitationOutboxIntegrationTests.Time clock;
    @Autowired InvitationOutboxIntegrationTests.Transport transport;
    final HttpClient http=HttpClient.newHttpClient();
    final String chosen=UUID.randomUUID().toString();
    Zone home,away;
    Compte global,local,ordinary;
    String hash;

    @BeforeEach void fixtures(){
        jdbc.update("delete from invitation_outbox");jdbc.update("delete from invitation");jdbc.update("delete from compte");
        jdbc.update("delete from vehicule");jdbc.execute("truncate client_numero_reservation");
        jdbc.update("delete from client");jdbc.update("delete from zone");
        home=zones.saveAndFlush(new Zone("Zone test A"));away=zones.saveAndFlush(new Zone("Zone test B"));
        hash=passwords.encode(chosen);
        global=comptes.saveAndFlush(new Compte("global@workflow.test",hash,true,RoleTechnique.ADMIN,null,PerimetreAdmin.GLOBAL,null));
        local=comptes.saveAndFlush(new Compte("local@workflow.test",hash,true,RoleTechnique.ADMIN,null,PerimetreAdmin.LOCAL,home));
        var client=clients.saveAndFlush(new Client("O990001",CategorieClient.OCCASIONNEL,true,null));
        ordinary=comptes.saveAndFlush(new Compte(client.getNumero(),hash,true,RoleTechnique.CLIENT,client,null,null));
        clock.now.set(Instant.now());transport.tokens.clear();transport.fail=false;
    }

    HttpResponse<String> post(String path,Object body,Compte actor) throws Exception{
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
        if(actor!=null) builder.header("Authorization","Bearer "+tokens.issue(actor.getId()).accessToken());
        return http.send(builder.POST(body==null?HttpRequest.BodyPublishers.noBody():
                HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode read(HttpResponse<String> response){return json.readTree(response.body());}
    Map<String,Object> clientBody(CategorieClient category,Long zone){
        var body=new HashMap<String,Object>();body.put("email","recipient@workflow.test");body.put("categorie",category.name());
        if(zone!=null)body.put("zoneClientId",zone);return body;
    }
    Map<String,Object> adminBody(PerimetreAdmin scope){
        var body=new HashMap<String,Object>();body.put("email","New.Admin@workflow.test");body.put("perimetreAdmin",scope.name());
        if(scope==PerimetreAdmin.LOCAL)body.put("zoneAdminId",home.getId());return body;
    }
    long createClient(CategorieClient category,Long zone,Compte actor)throws Exception{
        var response=post("/api/admin/clients",clientBody(category,zone),actor);
        assertThat(response.statusCode()).isEqualTo(201);return read(response).path("compte").path("compteId").asLong();
    }
    String deliver(){
        int before=transport.tokens.size();assertThat(delivery.dispatchNext()).isTrue();
        assertThat(transport.tokens.size()).isEqualTo(before+1);return transport.tokens.getLast();
    }
    HttpResponse<String> accept(String token,String password)throws Exception{
        return post("/api/auth/invitations/accept",Map.of("jeton",token,"password",password),null);
    }
    String renew(long id){return "/api/admin/comptes/"+id+"/invitations";}
    void noSecrets(String body){
        assertThat(body.contains(chosen)).isFalse();
        assertThat(body.matches("(?s).*\\$2[aby]\\$.*")).isFalse();
        assertThat(body.contains("passwordHash")||body.contains("invitationEmail")||body.contains("contenu_chiffre")
                ||body.contains("jeton")||body.contains("encryptionKey")).isFalse();
        for(String token:transport.tokens)assertThat(body.contains(token)).isFalse();
    }

    @ParameterizedTest @EnumSource(CategorieClient.class)
    void globalCreatesEveryClientCategoryAtomically(CategorieClient category)throws Exception{
        var response=post("/api/admin/clients",clientBody(category,category==CategorieClient.LOCAL?home.getId():null),global);
        assertThat(response.statusCode()).isEqualTo(201);
        var dto=read(response).path("compte");long id=dto.path("compteId").asLong();
        assertThat(dto.path("role").asText()).isEqualTo("CLIENT");
        assertThat(dto.path("categorie").asText()).isEqualTo(category.name());
        assertThat(dto.path("compteActif").asBoolean()&&dto.path("clientActif").asBoolean()).isTrue();
        assertThat(dto.path("accesEffectif").asBoolean()).isFalse();
        assertThat(dto.path("etatIdentifiants").asText()).isEqualTo("INVITE");
        assertThat(dto.path("identifiant").asText().equals(dto.path("numeroClient").asText())).isTrue();
        assertThat(dto.path("numeroClient").asText()).matches(category==CategorieClient.VIP?"V[0-9]{4,}":category==CategorieClient.LOCAL?"C[0-9]{4,}":"O[0-9]{4,}");
        assertThat(response.headers().firstValue("Location").orElse("")).endsWith("/"+id);
        assertThat(jdbc.queryForObject("select password_hash is null and invitation_email=? from compte where id=?",Boolean.class,"recipient@workflow.test",id)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from invitation_outbox",Integer.class)).isEqualTo(1);
        assertThat(post("/api/auth/login",Map.of("identifiant",dto.path("identifiant").asText(),"password",chosen),null).statusCode()).isEqualTo(401);
        noSecrets(response.body());
    }
    @Test void localCreatesOnlyLocalInOwnZone()throws Exception{
        long id=createClient(CategorieClient.LOCAL,home.getId(),local);
        assertThat(comptes.findById(id).orElseThrow().getClient().getZone().getId()).isEqualTo(home.getId());
    }
    @ParameterizedTest @EnumSource(value=CategorieClient.class,names={"VIP","OCCASIONNEL"})
    void localCannotCreateOtherCategoriesEvenInOwnZone(CategorieClient category)throws Exception{
        assertThat(post("/api/admin/clients",clientBody(category,home.getId()),local).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from invitation",Integer.class)).isZero();
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void localCannotCreateOutsideItsZoneWithoutDisclosingExistence(boolean exists)throws Exception{
        assertThat(post("/api/admin/clients",clientBody(CategorieClient.LOCAL,exists?away.getId():Long.MAX_VALUE),local).statusCode()).isEqualTo(403);
    }
    @ParameterizedTest @EnumSource(PerimetreAdmin.class)
    void globalCreatesAdministratorsWithCanonicalEmail(PerimetreAdmin scope)throws Exception{
        var response=post("/api/admin/administrateurs",adminBody(scope),global);
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(read(response).path("compte").path("identifiant").asText()).isEqualTo("new.admin@workflow.test");
        assertThat(read(response).path("compte").path("perimetreAdmin").asText()).isEqualTo(scope.name());
        noSecrets(response.body());
    }
    @ParameterizedTest @EnumSource(PerimetreAdmin.class)
    void localCannotCreateAnyAdministrator(PerimetreAdmin scope)throws Exception{
        assertThat(post("/api/admin/administrateurs",adminBody(scope),local).statusCode()).isEqualTo(403);
    }
    @ParameterizedTest @ValueSource(strings={"/api/admin/clients","/api/admin/administrateurs","/api/admin/comptes/1/invitations"})
    void administrativeEndpointsRequireAuthenticationAndAdminRole(String path)throws Exception{
        assertThat(post(path,Map.of(),null).statusCode()).isEqualTo(401);
        assertThat(post(path,Map.of(),ordinary).statusCode()).isEqualTo(403);
    }
    @ParameterizedTest @ValueSource(strings={"email","badEmail","categorie","zone","unknownZone","role","numero","actif","password","clientId","perimetreAdmin","zoneAdminId"})
    void invalidClientRequestsAreRejectedWithoutPartialWrites(String kind)throws Exception{
        var body=clientBody(CategorieClient.LOCAL,home.getId());
        switch(kind){
            case "email","categorie" -> body.remove(kind);
            case "badEmail" -> body.put("email","bad");
            case "zone" -> body.remove("zoneClientId");
            case "unknownZone" -> body.put("zoneClientId",Long.MAX_VALUE);
            default -> body.put(kind,kind.equals("actif")?false:"forbidden");
        }
        long before=comptes.count();
        var response=post("/api/admin/clients",body,global);
        assertThat(response.statusCode()).isEqualTo(kind.equals("unknownZone")?404:400);
        assertThat(comptes.count()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from invitation",Integer.class)).isZero();
        noSecrets(response.body());
    }
    @ParameterizedTest @ValueSource(strings={"globalZone","localNoZone","unknownZone","role"})
    void invalidAdministratorShapesAreRejected(String kind)throws Exception{
        var body=adminBody(PerimetreAdmin.LOCAL);
        switch(kind){
            case "globalZone" -> body.put("perimetreAdmin","GLOBAL");
            case "localNoZone" -> body.remove("zoneAdminId");
            case "unknownZone" -> body.put("zoneAdminId",Long.MAX_VALUE);
            default -> body.put("role","ADMIN");
        }
        assertThat(post("/api/admin/administrateurs",body,global).statusCode()).isEqualTo(kind.equals("unknownZone")?404:400);
    }
    @Test void duplicateAdministratorEmailIsCaseInsensitiveAndGeneric()throws Exception{
        assertThat(post("/api/admin/administrateurs",adminBody(PerimetreAdmin.GLOBAL),global).statusCode()).isEqualTo(201);
        var body=adminBody(PerimetreAdmin.GLOBAL);body.put("email","NEW.ADMIN@WORKFLOW.TEST");
        var response=post("/api/admin/administrateurs",body,global);
        assertThat(response.statusCode()).isEqualTo(409);noSecrets(response.body());
        assertThat(response.body().contains("duplicate key")||response.body().contains("org.postgresql")).isFalse();
    }
    @Test void creationRollsBackClientNumberAccountProofAndOutboxOnFailure()throws Exception{
        long before=comptes.count(),clientCount=clients.count();
        long reservations=jdbc.queryForObject("select count(*) from client_numero_reservation",Long.class);
        jdbc.execute("alter table invitation_outbox add constraint forced_failure check(false)");
        try{
            assertThat(post("/api/admin/clients",clientBody(CategorieClient.LOCAL,home.getId()),global).statusCode()).isEqualTo(409);
        }finally{jdbc.execute("alter table invitation_outbox drop constraint forced_failure");}
        assertThat(comptes.count()).isEqualTo(before);assertThat(clients.count()).isEqualTo(clientCount);
        assertThat(jdbc.queryForObject("select count(*) from client_numero_reservation",Long.class)).isEqualTo(reservations);
        assertThat(jdbc.queryForObject("select count(*) from invitation",Integer.class)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"GLOBAL","ADMIN_LOCAL","VIP","LOCAL","OCCASIONNEL"})
    void acceptanceEnablesNormalLoginForAllFiveProfiles(String profile)throws Exception{
        HttpResponse<String> created;
        if(profile.equals("GLOBAL")||profile.equals("ADMIN_LOCAL")){
            created=post("/api/admin/administrateurs",adminBody(profile.equals("GLOBAL")?PerimetreAdmin.GLOBAL:PerimetreAdmin.LOCAL),global);
        }else{
            var category=CategorieClient.valueOf(profile);
            created=post("/api/admin/clients",clientBody(category,category==CategorieClient.LOCAL?home.getId():null),global);
        }
        assertThat(created.statusCode()).isEqualTo(201);
        long id=read(created).path("compte").path("compteId").asLong();
        String login=read(created).path("compte").path("identifiant").asText();
        String token=deliver();
        var response=accept(token,chosen);assertThat(response.statusCode()).isEqualTo(204);assertThat(response.body()).isEmpty();
        var ready=comptes.findById(id).orElseThrow();
        assertThat(ready.getIdentifiant()).isEqualTo(login);
        assertThat(passwords.matches(chosen,ready.getPasswordHash())).isTrue();
        var signed=post("/api/auth/login",Map.of("identifiant",login.toLowerCase(Locale.ROOT),"password",chosen),null);
        assertThat(signed.statusCode()).isEqualTo(200);
        var me=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/auth/me"))
                .header("Authorization","Bearer "+read(signed).path("accessToken").asText()).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(me.statusCode()).isEqualTo(200);assertThat(read(me).path("compteId").asLong()).isEqualTo(id);
        noSecrets(me.body());
    }
    @ParameterizedTest @ValueSource(strings={"expired","revoked","used","invalid"})
    void unavailableInvitationsReturnSameGeneric400(String kind)throws Exception{
        long id=createClient(CategorieClient.LOCAL,home.getId(),global);String token=deliver();
        switch(kind){
            case "expired" -> clock.now.set(clock.instant().plusSeconds(25*3600));
            case "revoked" -> invitations.revoquer(id);
            case "used" -> assertThat(accept(token,chosen).statusCode()).isEqualTo(204);
            default -> token="A".repeat(43);
        }
        var response=accept(token,chosen);assertThat(response.statusCode()).isEqualTo(400);
        assertThat(read(response).path("code").asText()).isEqualTo("INVALID_INVITATION");noSecrets(response.body());
    }
    @ParameterizedTest @ValueSource(strings={"short","bytes","unicodeBytes","emojiShort","blank","extra"})
    void acceptanceValidatesCodePointsBytesAndForbiddenFields(String kind)throws Exception{
        long id=createClient(CategorieClient.LOCAL,home.getId(),global);String token=deliver();
        String value=switch(kind){case "short"->"a".repeat(14);case "bytes"->"a".repeat(73);case "unicodeBytes"->"é".repeat(37);
            case "emojiShort"->"😀".repeat(14);case "blank"->" ".repeat(15);default->chosen;};
        var body=new HashMap<String,Object>();body.put("jeton",token);body.put("password",value);
        if(kind.equals("extra"))body.put("actif",true);
        assertThat(post("/api/auth/invitations/accept",body,null).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("select consommee_a is null from invitation where compte_id=?",Boolean.class,id)).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"minimum","maximum","unicode","emoji"})
    void passwordBoundariesAreAcceptedWithoutCompositionRequirement(String kind)throws Exception{
        createClient(CategorieClient.LOCAL,home.getId(),global);
        String value=switch(kind){case "minimum"->"a".repeat(15);case "maximum"->"a".repeat(72);
            case "unicode"->"é".repeat(36);default->"😀".repeat(15);};
        assertThat(accept(deliver(),value).statusCode()).isEqualTo(204);
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void acceptanceNeverReactivatesDisabledAccountOrClient(boolean disableAccount)throws Exception{
        long id=createClient(CategorieClient.LOCAL,home.getId(),global);String token=deliver();
        if(disableAccount)jdbc.update("update compte set actif=false where id=?",id);
        else jdbc.update("update client set actif=false where id=(select client_id from compte where id=?)",id);
        assertThat(accept(token,chosen).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("select password_hash is null and etat_identifiants='INVITE' from compte where id=?",Boolean.class,id)).isTrue();
        assertThat(jdbc.queryForObject("select consommee_a is null from invitation where compte_id=?",Boolean.class,id)).isTrue();
    }
    @Test void renewRevokesPreviousTokenAndUsesOnlyStoredRecipient()throws Exception{
        long id=createClient(CategorieClient.LOCAL,home.getId(),local);String old=deliver();
        var response=post(renew(id),null,local);assertThat(response.statusCode()).isEqualTo(202);
        assertThat(read(response).path("statut").asText()).isEqualTo("PENDING");noSecrets(response.body());
        assertThat(accept(old,chosen).statusCode()).isEqualTo(400);
        assertThat(accept(deliver(),chosen).statusCode()).isEqualTo(204);
        assertThat(post(renew(id),null,global).statusCode()).isEqualTo(409);
    }
    @Test void renewalCannotRedirectDeliveryOrModifySecurityFields()throws Exception{
        long id=createClient(CategorieClient.LOCAL,home.getId(),local);
        assertThat(post(renew(id),Map.of("email","other@workflow.test"),local).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("select count(*) from invitation where compte_id=?",Integer.class,id)).isEqualTo(1);
    }
    @Test void renewalCancelsPendingCiphertextAndKeepsNumber()throws Exception{
        long id=createClient(CategorieClient.LOCAL,home.getId(),local);
        String number=comptes.findById(id).orElseThrow().getIdentifiant();
        assertThat(post(renew(id),null,local).statusCode()).isEqualTo(202);
        assertThat(jdbc.queryForObject("select count(*) from invitation_outbox where statut='CANCELLED' and contenu_chiffre is null",Integer.class)).isEqualTo(1);
        assertThat(comptes.findById(id).orElseThrow().getIdentifiant()).isEqualTo(number);
    }
    @Test void renewalIsInvisibleOutsideScopeAndForbiddenForVipInScope()throws Exception{
        long awayId=createClient(CategorieClient.LOCAL,away.getId(),global);
        long vipId=createClient(CategorieClient.VIP,home.getId(),global);
        var invisible=post(renew(awayId),null,local);var absent=post(renew(Long.MAX_VALUE),null,local);
        assertThat(invisible.statusCode()).isEqualTo(404);assertThat(absent.statusCode()).isEqualTo(404);
        assertThat(read(invisible).path("message").asText()).isEqualTo(read(absent).path("message").asText());
        assertThat(post(renew(global.getId()),null,local).statusCode()).isEqualTo(404);
        assertThat(post(renew(vipId),null,local).statusCode()).isEqualTo(403);
    }
    @ParameterizedTest @ValueSource(strings={"inactiveAccount","inactiveClient","legacy","initialized"})
    void incompatibleRenewalIs409AndDoesNotIssueAnotherToken(String kind)throws Exception{
        long id=createClient(CategorieClient.LOCAL,home.getId(),global);
        switch(kind){
            case "inactiveAccount"->jdbc.update("update compte set actif=false where id=?",id);
            case "inactiveClient"->jdbc.update("update client set actif=false where id=(select client_id from compte where id=?)",id);
            case "legacy"->jdbc.update("update compte set invitation_email=null where id=?",id);
            default->assertThat(accept(deliver(),chosen).statusCode()).isEqualTo(204);
        }
        assertThat(post(renew(id),null,global).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from invitation where compte_id=?",Integer.class,id)).isEqualTo(1);
    }
    @Test void concurrentAcceptanceThroughHttpSucceedsExactlyOnce()throws Exception{
        createClient(CategorieClient.LOCAL,home.getId(),global);String token=deliver();
        try(var pool=Executors.newFixedThreadPool(2)){
            var barrier=new CyclicBarrier(2);
            Callable<Integer> task=()->{barrier.await(10,TimeUnit.SECONDS);return accept(token,chosen).statusCode();};
            var a=pool.submit(task);var b=pool.submit(task);
            assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(204,400);
        }
    }
    @Test void concurrentDuplicateCreationIsOne201AndOne409()throws Exception{
        try(var pool=Executors.newFixedThreadPool(2)){
            var barrier=new CyclicBarrier(2);
            Callable<Integer> task=()->{barrier.await(10,TimeUnit.SECONDS);return post("/api/admin/administrateurs",adminBody(PerimetreAdmin.LOCAL),global).statusCode();};
            var a=pool.submit(task);var b=pool.submit(task);
            assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        }
        assertThat(jdbc.queryForObject("select count(*) from invitation",Integer.class)).isEqualTo(1);
    }
    @Test void acceptedPasswordMinimumIsNotRetroactiveForExistingAccounts()throws Exception{
        String legacy=UUID.randomUUID().toString().substring(0,8);
        jdbc.update("update compte set password_hash=? where id=?",passwords.encode(legacy),global.getId());
        assertThat(post("/api/auth/login",Map.of("identifiant",global.getIdentifiant(),"password",legacy),null).statusCode()).isEqualTo(200);
    }
    @Test void bootstrapIsSerializedAndNeverCreatesASecondGlobal(){
        jdbc.update("delete from compte where id=?",global.getId());
        try(var pool=Executors.newFixedThreadPool(2)){
            var barrier=new CyclicBarrier(2);
            Callable<Boolean> task=()->{barrier.await(10,TimeUnit.SECONDS);try{service.bootstrapGlobal("operator@workflow.test");return true;}
                catch(InvitationConflictException ex){return false;}};
            var a=pool.submit(task);var b=pool.submit(task);
            assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }catch(Exception ex){throw new AssertionError(ex);}
        assertThat(jdbc.queryForObject("select count(*) from compte where perimetre_admin='GLOBAL' and etat_identifiants='INVITE' and password_hash is null",Integer.class)).isEqualTo(1);
    }
    @Test void bootstrapRefusesEvenInactiveOrInvitedExistingGlobal(){
        jdbc.update("update compte set actif=false,etat_identifiants='INVITE',password_hash=null where id=?",global.getId());
        assertThatThrownBy(()->service.bootstrapGlobal("operator@workflow.test")).isInstanceOf(InvitationConflictException.class);
    }
    @Test void v7ToV8PreservesExistingIdentityHashActivitiesAndRelations(){
        String schema="contact_upgrade";
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("7").load().migrate();
        jdbc.update("insert into "+schema+".compte(id,identifiant,password_hash,actif,role,perimetre_admin) values(9001,'legacy@workflow.test',?,false,'ADMIN','GLOBAL')",hash);
        var upgraded=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("8").load();
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("select id=9001 and password_hash=? and not actif and etat_identifiants='INITIALISE' and invitation_email is null from "+schema+".compte",Boolean.class,hash)).isTrue();
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
    }
    @Test void swaggerDescribesAllFourEndpointsAndNoPublicAdministrativeAccess()throws Exception{
        var response=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/v3/api-docs")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var paths=read(response).path("paths");
        for(String path:List.of("/api/admin/clients","/api/admin/administrateurs","/api/admin/comptes/{id}/invitations","/api/auth/invitations/accept")){
            assertThat(paths.path(path).has("post")).isTrue();
        }
        assertThat(paths.path("/api/admin/clients").path("post").path("responses").path("403")
                .path("content").path("*/*").path("schema").path("$ref").asText()).endsWith("/ApiErrorResponse");
        assertThat(paths.path("/api/auth/invitations/accept").path("post").path("security").isMissingNode()
                ||paths.path("/api/auth/invitations/accept").path("post").path("security").isEmpty()).isTrue();
    }
}
