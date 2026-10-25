package com.plateforme.covoiturage;

import com.plateforme.covoiturage.entity.Client;
import com.plateforme.covoiturage.entity.Compte;
import com.plateforme.covoiturage.entity.Invitation;
import com.plateforme.covoiturage.entity.Zone;
import com.plateforme.covoiturage.enums.*;
import com.plateforme.covoiturage.exception.*;
import com.plateforme.covoiturage.repository.*;
import com.plateforme.covoiturage.security.InvitationProperties;
import com.plateforme.covoiturage.service.InvitationService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, InvitationIntegrationTests.TimeConfiguration.class})
@ActiveProfiles("dev")
@ExtendWith(OutputCaptureExtension.class)
class InvitationIntegrationTests {
    @Autowired InvitationService service;
    @Autowired InvitationRepository invitations;
    @Autowired CompteRepository comptes;
    @Autowired ClientRepository clients;
    @Autowired ZoneRepository zones;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired TransactionTemplate transaction;
    @Autowired PasswordEncoder passwords;
    @Autowired InvitationProperties properties;
    @Autowired ControlledClock clock;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;
    private Compte account;
    private static final Instant START = Instant.parse("2026-10-24T12:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary ControlledClock controlledClock() { return new ControlledClock(); }
    }

    static class ControlledClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>(START);
        void set(Instant value) { instant.set(value); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return instant.get(); }
    }

    @BeforeEach
    void fixtures() {
        // Exclusivement le conteneur PostgreSQL éphémère de ce contexte de test.
        jdbc.update("delete from invitation");
        jdbc.update("delete from compte");
        jdbc.update("delete from vehicule");
        jdbc.execute("truncate table client_numero_reservation");
        jdbc.update("delete from client");
        jdbc.update("delete from zone");
        clock.set(START);
        var zone = zones.saveAndFlush(new Zone("Invitation test zone"));
        var client = clients.saveAndFlush(new Client("C3401", CategorieClient.LOCAL, false, zone));
        account = comptes.saveAndFlush(Compte.invite("C3401", false, RoleTechnique.CLIENT, client, null, null));
    }

    @Test
    void cryptographicTokensAre32BytesDistinctAndOnlyDigestsArePersisted(CapturedOutput output) throws Exception {
        var seen = new HashSet<String>();
        for (int n = 0; n < 64; n++) {
            var issued = n == 0 ? service.emettre(account.getId()) : service.renouveler(account.getId());
            byte[] bytes = Base64.getUrlDecoder().decode(issued.jeton());
            assertThat(bytes.length).isEqualTo(32);
            assertThat(issued.jeton().matches("[A-Za-z0-9_-]{43}")).isTrue();
            assertThat(seen.add(issued.jeton())).isTrue();
            assertThat(issued.toString().contains(issued.jeton())).isFalse();
            assertThat(json.writeValueAsString(issued).contains(issued.jeton())).isFalse();
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            assertThat(jdbc.queryForObject("select count(*) from invitation where empreinte=?",
                    Integer.class, fingerprint)).isEqualTo(1);
        }
        String stored = jdbc.queryForList("select to_jsonb(i)::text from invitation i", String.class).toString();
        assertThat(seen.stream().anyMatch(stored::contains)).isFalse();
        assertThat(seen.stream().anyMatch(output.getAll()::contains)).isFalse();
        assertThat(invitations.count()).isEqualTo(64);
        assertThat(jdbc.queryForObject("select count(*) from invitation where revoquee_a is null",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns"
                + " where table_schema='public' and table_name='invitation' and column_name in ('jeton','token')",
                Integer.class)).isZero();
        // Test de non-déterminisme et de format ; la preuve cryptographique est SecureRandom (pas un test statistique).
    }

    @Test
    void developmentTtlIs24HoursAndTokenWorksImmediatelyBeforeExpiry() {
        assertThat(properties.ttl()).isEqualTo(Duration.ofHours(24));
        var issued = service.emettre(account.getId());
        assertThat(issued.expireA()).isEqualTo(START.plus(Duration.ofHours(24)));
        clock.set(issued.expireA().minusNanos(1000));
        assertThat(service.consommer(issued.jeton())).isEqualTo(account.getId());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 86400})
    void exactExpiryAndLaterAreRejectedWithoutDeletingAccountOrNumber(long secondsAfter) {
        var issued = service.emettre(account.getId());
        clock.set(issued.expireA().plusSeconds(secondsAfter));
        assertInvalid(issued.jeton());
        assertPreserved();
        assertThat(jdbc.queryForObject("select consommee_a is null from invitation", Boolean.class)).isTrue();
    }

    @Test
    void clockBeforeCreationCannotConsumeAProof() {
        var issued = service.emettre(account.getId());
        clock.set(START.minusSeconds(1));
        assertInvalid(issued.jeton());
    }

    @Test
    void consumedTokenIsNeverUsableAgain() {
        var issued = service.emettre(account.getId());
        assertThat(service.consommer(issued.jeton())).isEqualTo(account.getId());
        assertInvalid(issued.jeton());
        assertThat(jdbc.queryForObject("select count(*) from invitation where consommee_a is not null",
                Integer.class)).isEqualTo(1);
        assertPreserved();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "invalid", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB", "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"})
    void invalidUnknownOrNoncanonicalTokensHaveTheSameSafeFailure(String token) {
        assertInvalid(token);
        assertThat(invitations.count()).isZero();
    }

    @Test
    void revocationIsIdempotentAndDoesNotReactivateAnything() {
        var issued = service.emettre(account.getId());
        service.revoquer(account.getId());
        service.revoquer(account.getId());
        assertInvalid(issued.jeton());
        assertThat(jdbc.queryForObject("select revoquee_a is not null from invitation", Boolean.class)).isTrue();
        assertPreserved();
    }

    @Test
    void renewalRevokesPreviousProofAndKeepsHistoryAndAccount() {
        var first = service.emettre(account.getId());
        clock.set(first.expireA().plusSeconds(1));
        var next = service.renouveler(account.getId());
        assertThat(next.jeton().equals(first.jeton())).isFalse();
        assertInvalid(first.jeton());
        assertThat(next.expireA()).isEqualTo(clock.instant().plus(Duration.ofHours(24)));
        assertThat(service.consommer(next.jeton())).isEqualTo(account.getId());
        assertThat(invitations.count()).isEqualTo(2);
        assertPreserved();
    }

    @Test
    void issuingTwiceRequiresExplicitRenewal() {
        service.emettre(account.getId());
        assertThatThrownBy(() -> service.emettre(account.getId())).isInstanceOf(InvitationConflictException.class);
        assertThat(invitations.count()).isEqualTo(1);
    }

    @Test
    void initializedAccountCannotReceiveOrConsumeInvitations() {
        var issued = service.emettre(account.getId());
        jdbc.update("update compte set etat_identifiants='INITIALISE',password_hash=? where id=?",
                passwords.encode(UUID.randomUUID().toString()), account.getId());
        assertThatThrownBy(() -> service.emettre(account.getId())).isInstanceOf(InvitationConflictException.class);
        assertThatThrownBy(() -> service.renouveler(account.getId())).isInstanceOf(InvitationConflictException.class);
        assertInvalid(issued.jeton());
        assertThat(jdbc.queryForObject("select consommee_a is null from invitation", Boolean.class)).isTrue();
    }

    @Test
    void nonexistentAccountUsesTheExistingNotFoundException() {
        assertThatThrownBy(() -> service.emettre(Long.MAX_VALUE)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.revoquer(Long.MAX_VALUE)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void twoConcurrentPostgresTransactionsConsumeExactlyOnce() throws Exception {
        String token = service.emettre(account.getId()).jeton();
        var results = race(() -> {
            try { service.consommer(token); return true; }
            catch (InvitationInvalideException expected) { return false; }
        });
        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from invitation where consommee_a is not null",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void twoConcurrentIssuancesHaveExactlyOneOpenInvitation() throws Exception {
        var results = race(() -> {
            try { service.emettre(account.getId()); return true; }
            catch (InvitationConflictException expected) { return false; }
        });
        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        assertThat(invitations.count()).isEqualTo(1);
    }

    @Test
    void renewalAndConsumptionShareTheSameAccountLock() throws Exception {
        String previous = service.emettre(account.getId()).jeton();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var renewal = executor.submit(() -> { ready.countDown(); start.await(); return service.renouveler(account.getId()); });
            var consumption = executor.submit(() -> {
                ready.countDown(); start.await();
                try { service.consommer(previous); return true; }
                catch (InvitationInvalideException expected) { return false; }
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var next = renewal.get(20, TimeUnit.SECONDS);
            consumption.get(20, TimeUnit.SECONDS);
            assertInvalid(previous);
            assertThat(service.consommer(next.jeton())).isEqualTo(account.getId());
        } finally { start.countDown(); }
        assertThat(invitations.count()).isEqualTo(2);
    }

    @Test
    void issuanceRollbackDoesNotLeaveAProofOrLoseTheAccount() {
        var raw = new AtomicReference<String>();
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            raw.set(service.emettre(account.getId()).jeton());
            throw new IllegalStateException("test rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(invitations.count()).isZero();
        assertInvalid(raw.get());
        assertPreserved();
    }

    @Test
    void consumptionRollbackAllowsARetryInTheFutureAcceptanceTransaction() {
        var issued = service.emettre(account.getId());
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.consommer(issued.jeton());
            throw new IllegalStateException("test rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(service.consommer(issued.jeton())).isEqualTo(account.getId());
    }

    @Test
    void renewalRollbackKeepsTheOldProofAndRejectsTheRolledBackNewOne() {
        var first = service.emettre(account.getId());
        var raw = new AtomicReference<String>();
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            raw.set(service.renouveler(account.getId()).jeton());
            throw new IllegalStateException("test rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertInvalid(raw.get());
        assertThat(service.consommer(first.jeton())).isEqualTo(account.getId());
        assertThat(invitations.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"raw-token", "uppercase", "null-hash", "unknown-account", "null-account",
            "zero-expiry", "negative-expiry", "early-consumption", "expired-consumption",
            "early-revocation", "both-terminal", "duplicate-hash", "second-open"})
    void postgresEnforcesV6Constraints(String scenario) {
        String fingerprint = "a".repeat(64);
        Long id = account.getId();
        Instant expiry = START.plusSeconds(60);
        Instant consumed = null;
        Instant revoked = null;
        switch (scenario) {
            case "raw-token" -> fingerprint = "not-a-fingerprint";
            case "uppercase" -> fingerprint = "A".repeat(64);
            case "null-hash" -> fingerprint = null;
            case "unknown-account" -> id = Long.MAX_VALUE;
            case "null-account" -> id = null;
            case "zero-expiry" -> expiry = START;
            case "negative-expiry" -> expiry = START.minusSeconds(1);
            case "early-consumption" -> consumed = START.minusSeconds(1);
            case "expired-consumption" -> consumed = expiry;
            case "early-revocation" -> revoked = START.minusSeconds(1);
            case "both-terminal" -> { consumed = START; revoked = START; }
            case "duplicate-hash" -> {
                sqlInvitation(id, fingerprint, START, expiry, START, null);
            }
            case "second-open" -> {
                sqlInvitation(id, "b".repeat(64), START, expiry, null, null);
            }
            default -> throw new AssertionError("unknown constraint scenario");
        }
        boolean rejected = false;
        try { sqlInvitation(id, fingerprint, START, expiry, consumed, revoked); }
        catch (DataIntegrityViolationException expected) { rejected = true; }
        assertThat(rejected).as("V6 rejects inconsistent invitation: %s", scenario).isTrue();
    }

    @Test
    void invitationForeignKeyDoesNotCascadeDeleteAnAccount() {
        service.emettre(account.getId());
        boolean rejected = false;
        try { jdbc.update("delete from compte where id=?", account.getId()); }
        catch (DataIntegrityViolationException expected) { rejected = true; }
        assertThat(rejected).isTrue();
        assertPreserved();
    }

    @Test
    void v5ToV6PreservesExistingInitializedAndInvitedAccountsAndNumberHistory() {
        String schema = "invitation_" + UUID.randomUUID().toString().replace("-", "");
        var v5 = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("5").load();
        v5.migrate();
        jdbc.update("insert into " + schema + ".zone(id,nom) values(10,'Existing zone')");
        jdbc.update("insert into " + schema + ".client(id,numero,categorie,actif,zone_id)"
                + " values(11,'C3501','LOCAL',false,10)");
        String hash = passwords.encode(UUID.randomUUID().toString());
        jdbc.update("insert into " + schema + ".compte(id,identifiant,password_hash,actif,role,client_id)"
                + " values(21,'C3501',?,false,'CLIENT',11)", hash);
        jdbc.update("insert into " + schema + ".compte(id,identifiant,password_hash,etat_identifiants,actif,role,perimetre_admin)"
                + " values(22,'invited@migration.test',null,'INVITE',false,'ADMIN','GLOBAL')");
        String select = "select id,identifiant,etat_identifiants,actif,role,client_id,perimetre_admin,zone_admin_id from "
                + schema + ".compte order by id";
        var before = jdbc.queryForList(select);
        var v6 = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("6").load();
        assertThat(v6.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(v6.validateWithResult().validationSuccessful).isTrue();
        assertThat(v6.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForList(select)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select password_hash=? from " + schema + ".compte where id=21",
                Boolean.class, hash)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from " + schema + ".invitation", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select numero from " + schema + ".client_numero_reservation",
                String.class)).isEqualTo("C3501");
    }

    @Test
    void missingDurationOutsideDevFailsClosedBeforeAnyEmission() {
        var unconfigured = new InvitationService(comptes, invitations, new InvitationProperties(null), clock);
        assertThatThrownBy(() -> unconfigured.emettre(account.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(invitations.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT-1S"})
    void zeroAndNegativeConfiguredDurationsAreRejected(String value) {
        assertThatThrownBy(() -> new InvitationProperties(Duration.parse(value))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configuredDurationCanDifferFromTheDevelopmentDefault() {
        var configured = new InvitationService(comptes, invitations,
                new InvitationProperties(Duration.ofMinutes(30)), clock);
        var issued = transaction.execute(status -> configured.emettre(account.getId()));
        assertThat(issued.expireA()).isEqualTo(START.plusSeconds(1800));
    }

    @Test
    void invitationRoutesExposeOnlyPublicContractsWithoutInternalTokenSchemas() throws Exception {
        var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v3/api-docs")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var spec = json.readTree(response.body());
        assertThat(spec.path("paths").path("/api/auth/invitations/accept").has("post")).isTrue();
        assertThat(spec.path("paths").path("/api/admin/comptes/{id}/invitations").has("post")).isTrue();
        assertThat(spec.path("paths").has("/api/auth/invitations/emit")).isFalse();
        assertThat(spec.path("components").path("schemas").path("AcceptInvitationRequest")
                .path("properties").path("jeton").path("writeOnly").asBoolean()).isTrue();
        assertThat(spec.path("components").path("schemas").toString()).doesNotContain("InvitationEmise");
    }

    @Test
    void preloadedJpaInvitationCannotBeConsumedTwiceAfterAnotherCommit() {
        var issued = service.emettre(account.getId());
        String fingerprint = jdbc.queryForObject("select empreinte from invitation", String.class);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            invitations.findByEmpreinte(fingerprint).orElseThrow();
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.submit(() -> service.consommer(issued.jeton())).get(20, TimeUnit.SECONDS);
            } catch (Exception ex) { throw new IllegalStateException("concurrent setup failed", ex); }
            service.consommer(issued.jeton());
        })).isInstanceOf(InvitationInvalideException.class);
        assertThat(jdbc.queryForObject("select count(*) from invitation where consommee_a is not null",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void preloadedJpaAccountCannotBypassTheCurrentInitializedState() {
        String hash = passwords.encode(UUID.randomUUID().toString());
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            comptes.findById(account.getId()).orElseThrow();
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.submit(() -> jdbc.update("update compte set etat_identifiants='INITIALISE',password_hash=?"
                        + " where id=?", hash, account.getId())).get(20, TimeUnit.SECONDS);
            } catch (Exception ex) { throw new IllegalStateException("concurrent setup failed", ex); }
            service.emettre(account.getId());
        })).isInstanceOf(InvitationConflictException.class);
        assertThat(invitations.count()).isZero();
    }

    private void assertInvalid(String raw) {
        try {
            service.consommer(raw);
            throw new AssertionError("Invitation should be rejected");
        } catch (InvitationInvalideException expected) {
            assertThat(expected.getMessage()).isEqualTo("Invitation indisponible ou invalide");
        }
    }

    private void assertPreserved() {
        var reloaded = comptes.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getEtatIdentifiants()).isEqualTo(EtatIdentifiants.INVITE);
        assertThat(reloaded.getPasswordHash() == null).isTrue();
        assertThat(reloaded.isActif()).isFalse();
        assertThat(reloaded.getClient().isActif()).isFalse();
        assertThat(reloaded.getClient().getNumero()).isEqualTo("C3401");
        assertThat(reloaded.isAccesEffectif()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from client_numero_reservation where numero='C3401'",
                Integer.class)).isEqualTo(1);
    }

    private List<Boolean> race(Callable<Boolean> operation) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = java.util.stream.IntStream.range(0, 2).mapToObj(n -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return operation.call();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        } finally { start.countDown(); }
    }

    private void sqlInvitation(Long compteId, String fingerprint, Instant created, Instant expiry,
                               Instant consumed, Instant revoked) {
        jdbc.update("insert into invitation(compte_id,empreinte,cree_a,expire_a,consommee_a,revoquee_a)"
                        + " values(?,?,?,?,?,?)", compteId, fingerprint, java.sql.Timestamp.from(created),
                java.sql.Timestamp.from(expiry), consumed == null ? null : java.sql.Timestamp.from(consumed),
                revoked == null ? null : java.sql.Timestamp.from(revoked));
    }
}
