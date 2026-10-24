package com.plateforme.covoiturage;

import com.plateforme.covoiturage.dto.request.LoginRequest;
import com.plateforme.covoiturage.entity.Client;
import com.plateforme.covoiturage.entity.Compte;
import com.plateforme.covoiturage.enums.CategorieClient;
import com.plateforme.covoiturage.enums.PerimetreAdmin;
import com.plateforme.covoiturage.enums.RoleTechnique;
import com.plateforme.covoiturage.repository.ClientRepository;
import com.plateforme.covoiturage.repository.CompteRepository;
import com.plateforme.covoiturage.service.AuthService;
import com.plateforme.covoiturage.service.ClientNumberGenerator;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AccountIdentifierIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired ClientRepository clients;
    @Autowired CompteRepository comptes;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthService auth;
    @Autowired ClientNumberGenerator numbers;
    @Autowired JwtDecoder decoder;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DataSource dataSource;
    TransactionTemplate transaction;

    @BeforeEach
    @AfterEach
    void clearIsolatedFixtures() {
        transaction = new TransactionTemplate(transactions);
        // Testcontainers service connection only. Never a local development datasource.
        jdbc.update("delete from compte");
        jdbc.update("delete from vehicule");
        jdbc.execute("truncate table client_numero_reservation");
        jdbc.update("delete from client");
        jdbc.update("delete from zone");
    }

    @Test
    void databaseRequiresCanonicalAdministratorEmails() {
        String hash = newHash();
        comptes.saveAndFlush(new Compte("canonical@identity.test", hash, true,
                RoleTechnique.ADMIN, null, PerimetreAdmin.GLOBAL, null));
        for (String invalid : List.of("UPPER@identity.test", "old-admin-alias",
                "two@@identity.test", " padded@identity.test", "padded@identity.test ")) {
            assertThatThrownBy(() -> comptes.saveAndFlush(new Compte(invalid, hash, true,
                    RoleTechnique.ADMIN, null, PerimetreAdmin.GLOBAL, null)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(jdbc.queryForObject("select identifiant from compte", String.class))
                .isEqualTo("canonical@identity.test");
        assertThat(jdbc.queryForObject("select indexdef from pg_indexes where schemaname='public'"
                + " and indexname='uk_compte_identifiant_casefold'", String.class))
                .contains("UNIQUE", "lower");
    }

    @Test
    void clientAccountMustReferenceItsOwnUppercaseNumberAtCommit() {
        Client client = clients.saveAndFlush(new Client("V8101", CategorieClient.VIP, true, null));
        String hash = newHash();
        for (String mismatch : List.of("V8102", "v8101", "mail@client.test")) {
            assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                    comptes.saveAndFlush(new Compte(mismatch, hash, true, RoleTechnique.CLIENT,
                            client, null, null))))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(jdbc.queryForObject("select count(*) from compte", Integer.class)).isZero();
        comptes.saveAndFlush(new Compte(client.getNumero(), hash, true, RoleTechnique.CLIENT, client, null, null));
        assertThat(jdbc.queryForObject("select count(*) from compte", Integer.class)).isEqualTo(1);
    }

    @Test
    void changingOnlyOneSideRollsBackTheTransaction() {
        Client client = clients.saveAndFlush(new Client("V8201", CategorieClient.VIP, true, null));
        Compte account = comptes.saveAndFlush(new Compte(client.getNumero(), newHash(),
                true, RoleTechnique.CLIENT, client, null, null));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                jdbc.update("update client set numero='V8202' where id=?", client.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                jdbc.update("update compte set identifiant='V8202' where id=?", account.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select numero from client where id=?", String.class, client.getId()))
                .isEqualTo("V8201");
        assertThat(jdbc.queryForObject("select identifiant from compte where id=?", String.class, account.getId()))
                .isEqualTo("V8201");
        assertThat(jdbc.queryForObject(
                "select count(*) from client_numero_reservation where numero='V8202'", Integer.class)).isZero();
    }

    @Test
    void matchingUpdatesPreserveIdsHashesHistoryAndExistingJwtSubject() {
        String chosen = UUID.randomUUID().toString();
        String hash = passwords.encode(chosen);
        Client client = clients.saveAndFlush(new Client("V8301", CategorieClient.VIP, true, null));
        Compte account = comptes.saveAndFlush(new Compte(client.getNumero(), hash,
                true, RoleTechnique.CLIENT, client, null, null));
        var previousToken = auth.login(new LoginRequest("v8301", chosen));
        String subject = decoder.decode(previousToken.accessToken()).getSubject();
        String replacement = transaction.execute(status -> {
            String number = numbers.reserve(CategorieClient.VIP);
            // Same category: exercise deferred consistency, not a category-transition API.
            jdbc.update("update client set numero=? where id=?", number, client.getId());
            jdbc.update("update compte set identifiant=? where id=?", number, account.getId());
            return number;
        });
        var profile = auth.resolvePrincipal(subject).profile();
        assertThat(profile.compteId()).isEqualTo(account.getId());
        assertThat(profile.clientId()).isEqualTo(client.getId());
        assertThat(profile.identifiant()).isEqualTo(replacement);
        assertThat(profile.role()).isEqualTo(RoleTechnique.CLIENT);
        assertThat(hash.equals(jdbc.queryForObject("select password_hash from compte where id=?",
                String.class, account.getId()))).isTrue();
        assertThat(jdbc.queryForObject(
                "select retire_le is not null from client_numero_reservation where numero='V8301'",
                Boolean.class)).isTrue();
        assertThatThrownBy(() -> auth.login(new LoginRequest("V8301", chosen)))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(decoder.decode(auth.login(new LoginRequest(
                replacement.toLowerCase(java.util.Locale.ROOT), chosen)).accessToken()).getSubject())
                .isEqualTo(subject);
    }

    @Test
    void concurrentEmailVariantsHaveExactlyOneCommittedOwner() throws Exception {
        String hash = newHash();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of("Shared@Identity.test", "SHARED@IDENTITY.TEST").stream()
                    .map(email -> executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                        try {
                            transaction.executeWithoutResult(status -> jdbc.update(
                                    "insert into compte(identifiant,password_hash,actif,role,perimetre_admin)"
                                            + " values(lower(?),?,true,'ADMIN','GLOBAL')", email, hash));
                            return true;
                        } catch (DataIntegrityViolationException expected) {
                            return false;
                        }
                    })).toList();
            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int committed = 0;
            for (var future : futures) if (future.get(30, TimeUnit.SECONDS)) committed++;
            assertThat(committed).isEqualTo(1);
        } finally {
            start.countDown();
        }
        assertThat(jdbc.queryForObject("select count(*) from compte", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select identifiant from compte", String.class)).isEqualTo("shared@identity.test");
    }

    @Test
    void databaseCaseRulesAreSharedByPersistenceIndexAndLookup() {
        String input = "\u0130ADMIN@IDENTITY.TEST";
        String chosen = UUID.randomUUID().toString();
        jdbc.update("insert into compte(identifiant,password_hash,actif,role,perimetre_admin)"
                + " values(lower(?),?,true,'ADMIN','GLOBAL')", input, passwords.encode(chosen));
        String canonical = jdbc.queryForObject("select lower(?)", String.class, input);
        assertThat(jdbc.queryForObject("select identifiant from compte", String.class)).isEqualTo(canonical);
        String subject = decoder.decode(auth.login(new LoginRequest(input, chosen)).accessToken()).getSubject();
        assertThat(auth.resolvePrincipal(subject).profile().identifiant()).isEqualTo(canonical);
    }

    @Test
    void migrationFromV3PreservesCanonicalAccountsIdsHashesAndRelations() {
        String schema = schemaAtV3();
        String hash = newHash();
        jdbc.update("insert into " + schema + ".zone(id,nom) values (10,'Identity migration zone')");
        jdbc.update("insert into " + schema + ".client(id,numero,categorie,actif,zone_id)"
                + " values(31,'C8901','LOCAL',true,10)");
        jdbc.update("insert into " + schema + ".compte(id,identifiant,password_hash,actif,role,client_id)"
                + " values(41,'C8901',?,true,'CLIENT',31)", hash);
        jdbc.update("insert into " + schema + ".compte(id,identifiant,password_hash,actif,role,perimetre_admin,zone_admin_id)"
                + " values(42,'global@migration.test',?,false,'ADMIN','GLOBAL',null),"
                + "(43,'local@migration.test',?,true,'ADMIN','LOCAL',10)", hash, hash);
        var v4 = flyway(schema, "4");
        assertThat(v4.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(v4.validateWithResult().validationSuccessful).isTrue();
        assertThat(v4.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForList("select identifiant from " + schema + ".compte order by id", String.class))
                .containsExactly("C8901", "global@migration.test", "local@migration.test");
        assertThat(jdbc.queryForList("select id from " + schema + ".compte order by id", Long.class))
                .containsExactly(41L, 42L, 43L);
        assertThat(jdbc.queryForObject("select count(*) from " + schema
                + ".compte where password_hash=?", Integer.class, hash)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select client_id from " + schema + ".compte where id=41", Long.class))
                .isEqualTo(31L);
        assertThat(jdbc.queryForObject("select zone_admin_id from " + schema + ".compte where id=43", Long.class))
                .isEqualTo(10L);
        assertThat(jdbc.queryForObject("select actif from " + schema + ".compte where id=42", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from " + schema + ".client_numero_reservation", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void migrationRefusesLegacyAliasesCollisionsAndNoncanonicalEmailsWithoutRewriting() {
        for (String scenario : List.of("admin-alias", "admin-case", "client-alias", "collision")) {
            String schema = schemaAtV3();
            String hash = newHash();
            if (scenario.equals("client-alias")) {
                jdbc.update("insert into " + schema + ".client(id,numero,categorie,actif) values(31,'V8901','VIP',true)");
                jdbc.update("insert into " + schema + ".compte(identifiant,password_hash,actif,role,client_id)"
                        + " values('old-client-alias',?,true,'CLIENT',31)", hash);
            } else {
                String identifier = scenario.equals("admin-alias") ? "old-admin-alias" : "Mixed@Legacy.test";
                jdbc.update("insert into " + schema + ".compte(identifiant,password_hash,actif,role,perimetre_admin)"
                        + " values(?,?,true,'ADMIN','GLOBAL')", identifier, hash);
                if (scenario.equals("collision")) {
                    jdbc.update("insert into " + schema + ".compte(identifiant,password_hash,actif,role,perimetre_admin)"
                            + " values('mixed@legacy.test',?,true,'ADMIN','GLOBAL')", hash);
                }
            }
            var before = jdbc.queryForList("select identifiant from " + schema + ".compte order by id", String.class);
            assertThatThrownBy(() -> flyway(schema, "4").migrate()).isInstanceOf(FlywayException.class);
            assertThat(jdbc.queryForList("select identifiant from " + schema + ".compte order by id", String.class))
                    .isEqualTo(before);
            assertThat(jdbc.queryForObject("select count(*) from " + schema + ".compte where password_hash=?",
                    Integer.class, hash)).isEqualTo(before.size());
            assertThat(jdbc.queryForObject("select count(*) from pg_indexes where schemaname=?"
                    + " and indexname='uk_compte_identifiant_casefold'", Integer.class, schema)).isZero();
            assertThat(flyway(schema, "3").info().current().getVersion().getVersion()).isEqualTo("3");
        }
    }

    private String newHash() {
        return passwords.encode(UUID.randomUUID().toString());
    }

    private String schemaAtV3() {
        String schema = "identity_" + UUID.randomUUID().toString().replace("-", "");
        flyway(schema, "3").migrate();
        return schema;
    }

    private Flyway flyway(String schema, String version) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target(version).load();
    }
}
