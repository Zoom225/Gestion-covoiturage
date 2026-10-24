package com.plateforme.covoiturage;

import com.plateforme.covoiturage.delivery.*;
import com.plateforme.covoiturage.entity.Compte;
import com.plateforme.covoiturage.enums.*;
import com.plateforme.covoiturage.repository.CompteRepository;
import com.plateforme.covoiturage.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.system.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import({TestcontainersConfiguration.class, InvitationOutboxIntegrationTests.Fakes.class})
@ActiveProfiles("dev")
@ExtendWith(OutputCaptureExtension.class)
class InvitationOutboxIntegrationTests {
    static class Time extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());
        volatile CountDownLatch readSignal;
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return Clock.fixed(instant(),zone);}
        @Override public Instant instant(){var value=now.get();var signal=readSignal;if(signal!=null){readSignal=null;signal.countDown();}return value;}
    }
    static class Transport implements InvitationMailTransport {
        final List<String> tokens = new CopyOnWriteArrayList<>();
        volatile boolean fail;
        @Override public void send(UUID id,String recipient,String token) {
            if(fail) throw new IllegalStateException("unsafe-provider-detail:"+token);
            tokens.add(token);
        }
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class Fakes {
        @Bean @Primary Time time(){return new Time();}
        @Bean @Primary Transport transport(){return new Transport();}
        @Bean org.springframework.test.context.DynamicPropertyRegistrar deliveryTestProperties(){
            byte[] key=new byte[32]; new SecureRandom().nextBytes(key);
            String encoded=Base64.getEncoder().encodeToString(key);
            return registry -> {
                registry.add("driveconnect.delivery.key-id",()->"test-key");
                registry.add("driveconnect.delivery.encryption-key",()->encoded);
            };
        }
    }
    @Autowired InvitationService invitations;
    @Autowired InvitationDeliveryService delivery;
    @Autowired CompteRepository comptes;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;
    @Autowired DataSource dataSource;
    @Autowired Time clock;
    @Autowired Transport transport;
    private Compte account;

    @BeforeEach void fixtures(){
        jdbc.update("delete from invitation_outbox"); jdbc.update("delete from invitation"); jdbc.update("delete from compte");
        jdbc.update("delete from vehicule"); jdbc.execute("truncate table client_numero_reservation");
        jdbc.update("delete from client"); jdbc.update("delete from zone");
        clock.now.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        transport.tokens.clear(); transport.fail=false;
        account=comptes.saveAndFlush(Compte.invite("recipient@delivery.test",true,RoleTechnique.ADMIN,null,PerimetreAdmin.GLOBAL,null));
    }

    private InvitationService.InvitationEmise queue(){
        return transaction.execute(status -> {
            var invitation=invitations.emettre(account.getId());
            delivery.enqueue("recipient@delivery.test",invitation);
            return invitation;
        });
    }

    @Test void transactionalOutboxContainsNoPlaintextAndDispatchesOnlyOnce(CapturedOutput output){
        var issued=queue();
        String stored=jdbc.queryForObject("select to_jsonb(o)::text from invitation_outbox o",String.class);
        assertThat(stored.contains(issued.jeton())).isFalse();
        assertThat(stored.contains("recipient@delivery.test")).isFalse();
        assertThat(delivery.dispatchNext()).isTrue();
        assertThat(delivery.dispatchNext()).isFalse();
        assertThat(transport.tokens.size()).isEqualTo(1);
        assertThat(issued.jeton().equals(transport.tokens.getFirst())).isTrue();
        assertThat(jdbc.queryForObject("select statut='SENT' and contenu_chiffre is null from invitation_outbox",Boolean.class)).isTrue();
        assertThat(output.getAll().contains(issued.jeton())).isFalse();
    }

    @Test void failedCreationRollsBackBothProofAndEncryptedOutbox(){
        assertThatThrownBy(()->transaction.executeWithoutResult(status -> {
            var issued=invitations.emettre(account.getId());
            delivery.enqueue("recipient@delivery.test",issued);
            throw new IllegalStateException("rollback test");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from invitation",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from invitation_outbox",Integer.class)).isZero();
        assertThat(comptes.existsById(account.getId())).isTrue();
    }

    @Test void enqueueRequiresTheCallersTransaction(){
        var issued=invitations.emettre(account.getId());
        assertThatThrownBy(()->delivery.enqueue("recipient@delivery.test",issued))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @Test void unsafeSmtpFailureIsRetriedWithoutPersistingOrLoggingItsMessage(CapturedOutput output){
        var issued=queue(); transport.fail=true;
        assertThat(delivery.dispatchNext()).isTrue();
        assertThat(delivery.dispatchNext()).isFalse();
        assertThat(transport.tokens).isEmpty();
        assertThat(jdbc.queryForObject("select tentatives from invitation_outbox",Integer.class)).isEqualTo(1);
        String row=jdbc.queryForObject("select to_jsonb(o)::text from invitation_outbox o",String.class);
        assertThat(row.contains(issued.jeton()) || row.contains("unsafe-provider-detail")).isFalse();
        assertThat(output.getAll().contains(issued.jeton())).isFalse();
        clock.now.set(clock.instant().plusSeconds(60)); transport.fail=false;
        assertThat(delivery.dispatchNext()).isTrue();
        assertThat(transport.tokens.size()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings={"revoked","consumed","expired","initialized"})
    void obsoleteInvitationsAreCancelledAndTheirCiphertextIsRemoved(String scenario){
        var issued=queue();
        switch(scenario){
            case "revoked" -> invitations.revoquer(account.getId());
            case "consumed" -> invitations.consommer(issued.jeton());
            case "expired" -> clock.now.set(issued.expireA());
            case "initialized" -> jdbc.update("update compte set etat_identifiants='INITIALISE',password_hash=? where id=?",
                    passwords.encode(UUID.randomUUID().toString()),account.getId());
            default -> throw new AssertionError();
        }
        assertThat(delivery.dispatchNext()).isTrue();
        assertThat(transport.tokens).isEmpty();
        assertThat(jdbc.queryForObject("select statut='CANCELLED' and contenu_chiffre is null from invitation_outbox",Boolean.class)).isTrue();
    }

    @Test void concurrentDispatchesDoNotSendTheSameCommittedMessageTwice() throws Exception{
        queue();
        var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            var futures=java.util.stream.IntStream.range(0,2).mapToObj(n->executor.submit(()->{
                ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                return delivery.dispatchNext();
            })).toList();
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            int processed=0; for(var future:futures) if(future.get(20,TimeUnit.SECONDS)) processed++;
            assertThat(processed).isEqualTo(1);
        }finally{start.countDown();}
        assertThat(transport.tokens.size()).isEqualTo(1);
    }

    @Test void modifiedCiphertextNeverReachesTheTransport(){
        queue();
        byte[] encrypted=jdbc.queryForObject("select contenu_chiffre from invitation_outbox",byte[].class);
        encrypted[encrypted.length-1]^=1;
        jdbc.update("update invitation_outbox set contenu_chiffre=?",encrypted);
        assertThat(delivery.dispatchNext()).isTrue();
        assertThat(transport.tokens).isEmpty();
        assertThat(jdbc.queryForObject("select statut from invitation_outbox",String.class)).isEqualTo("PENDING");
    }

    @Test void v6ToV7PreservesExistingAccountsAndDoesNotQueueEmails(){
        String schema="outbox_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("6").load().migrate();
        jdbc.update("insert into "+schema+".compte(id,identifiant,etat_identifiants,password_hash,actif,role,perimetre_admin)"
                +" values(21,'legacy@delivery.test','INVITE',null,false,'ADMIN','GLOBAL')");
        var v7=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("7").load();
        assertThat(v7.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(v7.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForObject("select password_hash is null and not actif and etat_identifiants='INVITE' from "
                +schema+".compte where id=21",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from "+schema+".invitation_outbox",Integer.class)).isZero();
    }

    @Autowired AuthService auth;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired org.springframework.security.oauth2.jwt.JwtDecoder decoder;


    @ParameterizedTest @ValueSource(strings={"GLOBAL","ADMIN_LOCAL","VIP","LOCAL","OCCASIONNEL"})
    void atomicAcceptanceInitializesBcryptAndKeepsIdLoginAndNormalJwt(String profile,CapturedOutput output){
        account=profile(profile,true,true);
        var issued=queue();
        String identifier=account.getIdentifiant();
        Long id=account.getId();
        String chosen="ChoixPersonnel12!"+UUID.randomUUID();
        auth.accepterInvitation(issued.jeton(),chosen);
        var ready=comptes.findById(id).orElseThrow();
        assertThat(ready.getEtatIdentifiants()).isEqualTo(EtatIdentifiants.INITIALISE);
        assertThat(ready.getIdentifiant()).isEqualTo(identifier);
        assertThat(passwords.matches(chosen,ready.getPasswordHash())).isTrue();
        assertThat(ready.isAccesEffectif()).isTrue();
        var token=auth.login(new com.plateforme.covoiturage.dto.request.LoginRequest(identifier.toLowerCase(Locale.ROOT),chosen));
        assertThat(decoder.decode(token.accessToken()).getSubject()).isEqualTo(id.toString());
        assertThat(auth.resolvePrincipal(id.toString()).profile().role()).isEqualTo(ready.getRole());
        assertThat(jdbc.queryForObject("select statut='CANCELLED' and contenu_chiffre is null from invitation_outbox",Boolean.class)).isTrue();
        assertThat(output.getAll().contains(chosen) || output.getAll().contains(issued.jeton())).isFalse();
        assertThatThrownBy(()->auth.accepterInvitation(issued.jeton(),chosen))
                .isInstanceOf(com.plateforme.covoiturage.exception.InvitationInvalideException.class);
    }

    @ParameterizedTest @ValueSource(strings={"compte","client"})
    void acceptanceNeverReactivatesDisabledAccountsOrClientsAndLeavesProofUsable(String disabled){
        account=profile("LOCAL",!disabled.equals("compte"),!disabled.equals("client"));
        var issued=queue();
        assertThatThrownBy(()->auth.accepterInvitation(issued.jeton(),"ChoixPersonnel12!"+UUID.randomUUID()))
                .isInstanceOf(com.plateforme.covoiturage.exception.InvitationInvalideException.class);
        var unchanged=comptes.findById(account.getId()).orElseThrow();
        assertThat(unchanged.getEtatIdentifiants()).isEqualTo(EtatIdentifiants.INVITE);
        assertThat(unchanged.getPasswordHash()==null).isTrue();
        assertThat(unchanged.isActif()).isEqualTo(!disabled.equals("compte"));
        assertThat(unchanged.getClient().isActif()).isEqualTo(!disabled.equals("client"));
        assertThat(jdbc.queryForObject("select consommee_a is null from invitation",Boolean.class)).isTrue();
    }

    @Test void acceptanceAndPasswordInitializationRollbackTogether(){
        var issued=queue();
        String chosen="ChoixPersonnel12!"+UUID.randomUUID();
        assertThatThrownBy(()->transaction.executeWithoutResult(status->{
            auth.accepterInvitation(issued.jeton(),chosen);
            throw new IllegalStateException("test rollback");
        })).isInstanceOf(IllegalStateException.class);
        var unchanged=comptes.findById(account.getId()).orElseThrow();
        assertThat(unchanged.getPasswordHash()==null).isTrue();
        assertThat(unchanged.getEtatIdentifiants()).isEqualTo(EtatIdentifiants.INVITE);
        assertThat(jdbc.queryForObject("select consommee_a is null from invitation",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select statut from invitation_outbox",String.class)).isEqualTo("PENDING");
        auth.accepterInvitation(issued.jeton(),chosen);
        assertThat(comptes.findById(account.getId()).orElseThrow().getEtatIdentifiants()).isEqualTo(EtatIdentifiants.INITIALISE);
    }

    @Test void concurrentAcceptanceHasOnePasswordInitialization() throws Exception{
        var issued=queue();
        String chosen="ChoixPersonnel12!"+UUID.randomUUID();
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            var futures=java.util.stream.IntStream.range(0,2).mapToObj(n->executor.submit(()->{
                ready.countDown();start.await();
                try{auth.accepterInvitation(issued.jeton(),chosen);return true;}
                catch(com.plateforme.covoiturage.exception.InvitationInvalideException expected){return false;}
            })).toList();
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            int accepted=0;for(var future:futures)if(future.get(20,TimeUnit.SECONDS))accepted++;
            assertThat(accepted).isEqualTo(1);
        }finally{start.countDown();}
        assertThat(passwords.matches(chosen,comptes.findById(account.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @ParameterizedTest @ValueSource(strings={"","   ","bytes","unicode"})
    void existingBcryptLimitsRejectInvalidPasswordsWithoutConsumingTheProof(String kind){
        var issued=queue();
        String chosen=switch(kind){case "bytes"->"a".repeat(73);case "unicode"->"é".repeat(37);default->kind;};
        assertThatThrownBy(()->auth.accepterInvitation(issued.jeton(),chosen))
                .isInstanceOf(com.plateforme.covoiturage.exception.InvalidAccountRequestException.class);
        assertThat(jdbc.queryForObject("select consommee_a is null from invitation",Boolean.class)).isTrue();
    }

    private Compte profile(String profile,boolean accountActive,boolean clientActive){
        if(profile.equals("GLOBAL"))return account;
        var zones=new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        Long zoneId=zones.queryForObject("insert into zone(nom) values('Zone acceptance') returning id",Long.class);
        return transaction.execute(status->{
            var zone=entityManager.getReference(com.plateforme.covoiturage.entity.Zone.class,zoneId);
            if(profile.equals("ADMIN_LOCAL")){
                return comptes.saveAndFlush(Compte.invite("local@acceptance.test",accountActive,RoleTechnique.ADMIN,null,PerimetreAdmin.LOCAL,zone));
            }
            var category=CategorieClient.valueOf(profile);
            String number=switch(category){case VIP->"V4411";case LOCAL->"C4411";case OCCASIONNEL->"O4411";};
            var client=new com.plateforme.covoiturage.entity.Client(number,category,clientActive,category==CategorieClient.LOCAL?zone:null);
            entityManager.persist(client);entityManager.flush();
            return comptes.saveAndFlush(Compte.invite(number,accountActive,RoleTechnique.CLIENT,client,null,null));
        });
    }

    @Autowired jakarta.persistence.EntityManager entityManager;


    @ParameterizedTest @ValueSource(strings={"fk","nonce","state","bytes","pending-null","sent-cipher","sent-no-date","attempts"})
    void outboxConstraintsRejectInconsistentRows(String scenario){
        var proof=invitations.emettre(account.getId());
        Long invitation=scenario.equals("fk")?Long.MAX_VALUE:proof.invitationId();
        byte[] nonce=new byte[scenario.equals("nonce")?11:12];
        byte[] encrypted=scenario.equals("pending-null")?null:new byte[scenario.equals("bytes")?15:32];
        String state=switch(scenario){case "state"->"INVALID";case "sent-cipher","sent-no-date"->"SENT";default->"PENDING";};
        java.sql.Timestamp sent=scenario.equals("sent-cipher")?java.sql.Timestamp.from(clock.instant()):null;
        boolean rejected=false;
        try{
            jdbc.update("insert into invitation_outbox(id,invitation_id,key_id,nonce,contenu_chiffre,statut,cree_a,"
                            +"prochaine_tentative_a,tentatives,envoyee_a) values(?,?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(),invitation,"test-key",nonce,encrypted,state,
                    java.sql.Timestamp.from(clock.instant()),java.sql.Timestamp.from(clock.instant()),
                    scenario.equals("attempts")?-1:0,sent);
        }catch(org.springframework.dao.DataIntegrityViolationException expected){rejected=true;}
        assertThat(rejected).as("Outbox integrity: %s",scenario).isTrue();
    }


    @Test void dispatcherRechecksExpiryAfterWaitingForTheAccountLock() throws Exception{
        var issued=queue();
        var firstRead=new CountDownLatch(1);
        var future=new AtomicReference<Future<Boolean>>();
        try(var executor=Executors.newSingleThreadExecutor()){
            transaction.executeWithoutResult(status->{
                comptes.verrouillerEtatPourInvitation(account.getId()).orElseThrow();
                clock.readSignal=firstRead;
                future.set(executor.submit(delivery::dispatchNext));
                try{
                    assertThat(firstRead.await(10,TimeUnit.SECONDS)).isTrue();
                }catch(InterruptedException ex){
                    Thread.currentThread().interrupt();throw new IllegalStateException("test interrupted");
                }
                clock.now.set(issued.expireA());
            });
            assertThat(future.get().get(20,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(transport.tokens).isEmpty();
        assertThat(jdbc.queryForObject("select statut from invitation_outbox",String.class)).isEqualTo("CANCELLED");
    }

}
