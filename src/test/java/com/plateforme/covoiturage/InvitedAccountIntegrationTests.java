package com.plateforme.covoiturage;

import com.plateforme.covoiturage.dto.request.LoginRequest;
import com.plateforme.covoiturage.entity.Client;
import com.plateforme.covoiturage.entity.Compte;
import com.plateforme.covoiturage.entity.Zone;
import com.plateforme.covoiturage.enums.CategorieClient;
import com.plateforme.covoiturage.enums.EtatIdentifiants;
import com.plateforme.covoiturage.enums.PerimetreAdmin;
import com.plateforme.covoiturage.enums.RoleTechnique;
import com.plateforme.covoiturage.repository.ClientRepository;
import com.plateforme.covoiturage.repository.CompteRepository;
import com.plateforme.covoiturage.repository.ZoneRepository;
import com.plateforme.covoiturage.service.AuthService;
import com.plateforme.covoiturage.service.TokenService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
@ExtendWith(OutputCaptureExtension.class)
class InvitedAccountIntegrationTests {
    private final HttpClient http = HttpClient.newHttpClient();
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired CompteRepository comptes;
    @Autowired ClientRepository clients;
    @Autowired ZoneRepository zones;
    @Autowired AuthService auth;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder passwords;
    @Autowired TransactionTemplate transaction;
    @Autowired ObjectMapper json;
    private Zone home;
    private String chosenPassword;
    private String hash;

    @BeforeEach
    void fixtures() {
        // Exclusivement la base PostgreSQL jetable fournie par Testcontainers.
        jdbc.update("delete from compte");
        jdbc.update("delete from vehicule");
        jdbc.execute("truncate table client_numero_reservation");
        jdbc.update("delete from client");
        jdbc.update("delete from zone");
        home = zones.saveAndFlush(new Zone("Invitation test zone"));
        chosenPassword = UUID.randomUUID().toString();
        hash = passwords.encode(chosenPassword);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GLOBAL", "ADMIN_LOCAL", "VIP", "LOCAL", "OCCASIONNEL"})
    void invitedProfilesHaveNoPasswordAndCannotLoginOrUseASignedJwt(String profile) throws Exception {
        var account = invited(profile, true, true);
        var reloaded = comptes.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getEtatIdentifiants()).isEqualTo(EtatIdentifiants.INVITE);
        assertThat(reloaded.getPasswordHash() == null).isTrue();
        assertThat(reloaded.isAccesEffectif()).isFalse();
        assertThat(reloaded.isActif()).isTrue();

        var unknown = login("unknown@invitation.test");
        var invited = login(account.getIdentifiant().toLowerCase(java.util.Locale.ROOT));
        assertThat(invited.statusCode()).isEqualTo(401);
        assertThat(unknown.statusCode()).isEqualTo(401);
        assertThat(json.readTree(invited.body()).path("message").asText())
                .isEqualTo(json.readTree(unknown.body()).path("message").asText());
        assertSafe(invited.body());

        // Même un jeton correctement signé avec sub=Compte.id est refusé par la relecture en base.
        String token = tokens.issue(account.getId()).accessToken();
        assertThat(get("/api/auth/me", token).statusCode()).isEqualTo(401);
        assertThat(get("/api/admin/comptes", token).statusCode()).isEqualTo(401);
    }

    @ParameterizedTest
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void invitedCredentialStateDoesNotChangeEitherActivityFlag(boolean accountActive, boolean clientActive) {
        var account = invited("LOCAL", accountActive, clientActive);
        var reloaded = comptes.findById(account.getId()).orElseThrow();
        assertThat(reloaded.isActif()).isEqualTo(accountActive);
        assertThat(reloaded.getClient().isActif()).isEqualTo(clientActive);
        assertThat(reloaded.isAccesEffectif()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"invited-hash", "initialized-null", "initialized-plain", "initialized-empty",
            "initialized-cost00", "initialized-cost32", "unknown-state", "null-state"})
    void postgresRejectsInconsistentCredentialStates(String scenario) {
        String state = scenario.startsWith("invited") ? "INVITE" : "INITIALISE";
        String candidate = hash;
        switch (scenario) {
            case "initialized-null" -> candidate = null;
            case "initialized-plain" -> candidate = chosenPassword;
            case "initialized-empty" -> candidate = "";
            case "initialized-cost00" -> candidate = hash.substring(0, 4) + "00" + hash.substring(6);
            case "initialized-cost32" -> candidate = hash.substring(0, 4) + "32" + hash.substring(6);
            case "unknown-state" -> state = "UNKNOWN";
            case "null-state" -> state = null;
            default -> { }
        }
        boolean rejected = false;
        try {
            insertAdmin("invalid@invitation.test", state, candidate, true);
        } catch (DataIntegrityViolationException expected) {
            // Ne pas transmettre le détail PostgreSQL contenant potentiellement la ligne et son hash.
            rejected = true;
        }
        assertThat(rejected).as("PostgreSQL must reject inconsistent state: %s", scenario).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from compte", Integer.class)).isZero();
    }

    @Test
    void defaultPreservesInitializedWritersButNeverCreatesAHashlessInitializedAccount() {
        jdbc.update("insert into compte(identifiant,password_hash,actif,role,perimetre_admin)"
                + " values('existing-writer@invitation.test',?,false,'ADMIN','GLOBAL')", hash);
        var account = comptes.findByIdentifiantIgnoringCase("EXISTING-WRITER@INVITATION.TEST").orElseThrow();
        assertThat(account.getEtatIdentifiants()).isEqualTo(EtatIdentifiants.INITIALISE);
        assertThat(hash.equals(account.getPasswordHash())).isTrue();
        assertThat(account.isActif()).isFalse();
        assertThat(account.isAccesEffectif()).isFalse();
        boolean rejected = false;
        try {
            jdbc.update("insert into compte(identifiant,actif,role,perimetre_admin)"
                    + " values('missing-hash@invitation.test',true,'ADMIN','GLOBAL')");
        } catch (DataIntegrityViolationException expected) {
            rejected = true;
        }
        assertThat(rejected).isTrue();
    }

    @Test
    void initializedDomainConstructorStillSupportsLoginAndIdBasedJwt() {
        var account = comptes.saveAndFlush(new Compte("ready@invitation.test", hash, true,
                RoleTechnique.ADMIN, null, PerimetreAdmin.GLOBAL, null));
        assertThat(account.getEtatIdentifiants()).isEqualTo(EtatIdentifiants.INITIALISE);
        String token = auth.login(new LoginRequest("READY@INVITATION.TEST", chosenPassword)).accessToken();
        assertThat(token != null && !token.isBlank()).isTrue();
        assertThat(auth.resolvePrincipal(account.getId().toString()).profile().compteId()).isEqualTo(account.getId());
    }

    @Test
    void stateAndHashMustChangeTogetherAndFailedTransactionPreservesActivityAndHash() {
        var account = invited("GLOBAL", false, true);
        boolean rejected = false;
        try {
            transaction.executeWithoutResult(status -> {
                jdbc.update("update compte set actif=true where id=?", account.getId());
                jdbc.update("update compte set password_hash=? where id=?", hash, account.getId());
            });
        } catch (DataIntegrityViolationException expected) {
            rejected = true;
        }
        assertThat(rejected).isTrue();
        var unchanged = comptes.findById(account.getId()).orElseThrow();
        assertThat(unchanged.getPasswordHash() == null).isTrue();
        assertThat(unchanged.isActif()).isFalse();
        // Test de la contrainte seulement : aucun parcours applicatif de consommation n'est livré.
        jdbc.update("update compte set etat_identifiants='INITIALISE',password_hash=? where id=?", hash, account.getId());
        var initialized = comptes.findById(account.getId()).orElseThrow();
        assertThat(initialized.getEtatIdentifiants()).isEqualTo(EtatIdentifiants.INITIALISE);
        assertThat(initialized.isActif()).isFalse();
        assertThat(initialized.isAccesEffectif()).isFalse();
    }

    @Test
    void databaseReadinessChangeInvalidatesAnAlreadyIssuedJwtWithoutChangingActivity() throws Exception {
        var account = comptes.saveAndFlush(new Compte("jwt@invitation.test", hash, true,
                RoleTechnique.ADMIN, null, PerimetreAdmin.GLOBAL, null));
        String token = auth.login(new LoginRequest(account.getIdentifiant(), chosenPassword)).accessToken();
        assertThat(get("/api/auth/me", token).statusCode()).isEqualTo(200);
        jdbc.update("update compte set etat_identifiants='INVITE',password_hash=null where id=?", account.getId());
        assertThat(get("/api/auth/me", token).statusCode()).isEqualTo(401);
        assertThat(comptes.findById(account.getId()).orElseThrow().isActif()).isTrue();
    }

    @Test
    void adminReadExposesOnlySafeReadinessAndRetainsLocalScope() throws Exception {
        var global = comptes.saveAndFlush(new Compte("reader@invitation.test", hash, true,
                RoleTechnique.ADMIN, null, PerimetreAdmin.GLOBAL, null));
        var local = comptes.saveAndFlush(new Compte("local-reader@invitation.test", hash, true,
                RoleTechnique.ADMIN, null, PerimetreAdmin.LOCAL, home));
        var own = invited("LOCAL", true, true);
        var noZone = invited("VIP", true, true);
        var pendingAdmin = invited("GLOBAL", true, true);
        String globalToken = tokens.issue(global.getId()).accessToken();
        String localToken = tokens.issue(local.getId()).accessToken();
        for (var target : List.of(own, noZone, pendingAdmin)) {
            var response = get("/api/admin/comptes/" + target.getId(), globalToken);
            assertThat(response.statusCode()).isEqualTo(200);
            assertSafe(response.body());
            var body = json.readTree(response.body());
            assertThat(body.path("etatIdentifiants").asText()).isEqualTo("INVITE");
            assertThat(body.path("compteActif").asBoolean()).isTrue();
            assertThat(body.path("accesEffectif").asBoolean()).isFalse();
        }
        var page = get("/api/admin/comptes", localToken);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(json.readTree(page.body()).path("totalElements").asLong()).isEqualTo(1);
        assertThat(get("/api/admin/comptes/" + own.getId(), localToken).statusCode()).isEqualTo(200);
        for (var hidden : List.of(noZone, pendingAdmin)) {
            assertThat(get("/api/admin/comptes/" + hidden.getId(), localToken).statusCode()).isEqualTo(404);
        }
    }

    @Test
    void upgradeV4PreservesAllFiveProfilesIdsHashesRelationsAndIndependentActivityFlags() {
        String schema = schemaAt("4");
        jdbc.update("insert into " + schema + ".zone(id,nom) values(10,'Migration invitation')");
        jdbc.update("insert into " + schema + ".client(id,numero,categorie,actif,zone_id) values"
                + "(11,'V9101','VIP',false,null),(12,'C9101','LOCAL',true,10),(13,'O9101','OCCASIONNEL',true,null)");
        jdbc.update("insert into " + schema + ".compte(id,identifiant,password_hash,actif,role,perimetre_admin,zone_admin_id)"
                + " values(21,'global@migration.test',?,false,'ADMIN','GLOBAL',null),"
                + "(22,'local@migration.test',?,true,'ADMIN','LOCAL',10)", hash, hash);
        jdbc.update("insert into " + schema + ".compte(id,identifiant,password_hash,actif,role,client_id)"
                + " values(23,'V9101',?,true,'CLIENT',11),(24,'C9101',?,false,'CLIENT',12),"
                + "(25,'O9101',?,true,'CLIENT',13)", hash, hash, hash);
        // Snapshot sans hash ; le hash est vérifié par égalité SQL sans l'imprimer.
        String select = "select id,identifiant,actif,role,client_id,perimetre_admin,zone_admin_id from "
                + schema + ".compte order by id";
        var before = jdbc.queryForList(select);
        var clientBefore = jdbc.queryForList("select * from " + schema + ".client order by id");
        var migration = flyway(schema, "5");
        assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(migration.validateWithResult().validationSuccessful).isTrue();
        assertThat(migration.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForList(select)).isEqualTo(before);
        assertThat(jdbc.queryForList("select * from " + schema + ".client order by id")).isEqualTo(clientBefore);
        assertThat(jdbc.queryForObject("select count(*) from " + schema
                + ".compte where password_hash=? and etat_identifiants='INITIALISE'", Integer.class, hash)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from " + schema
                + ".client_numero_reservation", Integer.class)).isEqualTo(3);
    }

    @Test
    void upgradeV1ToV5PreservesReferentialsAndDoesNotSeedAccounts() {
        String schema = schemaAt("1");
        jdbc.update("insert into " + schema + ".zone(id,nom) values(10,'Existing zone')");
        jdbc.update("insert into " + schema + ".client(id,numero,categorie,actif,zone_id)"
                + " values(11,'C9201','LOCAL',false,10)");
        jdbc.update("insert into " + schema + ".vehicule(id,zone_id) values(12,10)");
        assertThat(flyway(schema, "5").migrate().migrationsExecuted).isEqualTo(4);
        assertThat(jdbc.queryForObject("select zone_id from " + schema + ".vehicule where id=12", Long.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("select actif from " + schema + ".client where id=11", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from " + schema + ".compte", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select numero from " + schema + ".client_numero_reservation", String.class))
                .isEqualTo("C9201");
    }

    @Test
    void unsupportedLegacyBcryptCostStopsMigrationWithoutRewritingOrLeakingHash() {
        String schema = schemaAt("4");
        String invalidHash = hash.substring(0, 4) + "00" + hash.substring(6);
        jdbc.update("insert into " + schema + ".compte(identifiant,password_hash,actif,role,perimetre_admin)"
                + " values('legacy@migration.test',?,false,'ADMIN','GLOBAL')", invalidHash);
        try {
            flyway(schema, "5").migrate();
            throw new AssertionError("Migration should reject incompatible legacy hash");
        } catch (FlywayException expected) {
            assertThat(expected.getMessage().contains(invalidHash)).isFalse();
        }
        assertThat(jdbc.queryForObject("select count(*) from " + schema + ".compte where password_hash=? and not actif",
                Integer.class, invalidHash)).isEqualTo(1);
        assertThat(flyway(schema, "4").info().current().getVersion().getVersion()).isEqualTo("4");
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=?"
                + " and table_name='compte' and column_name='etat_identifiants'", Integer.class, schema)).isZero();
    }

    @Test
    void constraintFailureDoesNotExposeHashInExceptionOrApplicationLogs(CapturedOutput output) {
        boolean rejected = false;
        try {
            comptes.saveAndFlush(new Compte("invalid-profile@invitation.test", hash, true,
                    RoleTechnique.ADMIN, null, PerimetreAdmin.LOCAL, null));
        } catch (DataIntegrityViolationException expected) {
            rejected = true;
            var writer = new java.io.StringWriter();
            expected.printStackTrace(new java.io.PrintWriter(writer));
            assertThat(writer.toString().contains(hash)).isFalse();
        }
        assertThat(rejected).isTrue();
        assertThat(output.getAll().contains(hash)).isFalse();
        assertThat(output.getAll().contains(chosenPassword)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from compte", Integer.class)).isZero();
    }
    private Compte invited(String profile, boolean accountActive, boolean clientActive) {
        if (profile.equals("GLOBAL") || profile.equals("ADMIN_LOCAL")) {
            boolean local = profile.equals("ADMIN_LOCAL");
            return comptes.saveAndFlush(Compte.invite(local ? "pending-local@invitation.test" : "pending-global@invitation.test",
                    accountActive, RoleTechnique.ADMIN, null,
                    local ? PerimetreAdmin.LOCAL : PerimetreAdmin.GLOBAL, local ? home : null));
        }
        var category = CategorieClient.valueOf(profile);
        String number = switch (category) { case VIP -> "V9301"; case LOCAL -> "C9301"; case OCCASIONNEL -> "O9301"; };
        var client = clients.saveAndFlush(new Client(number, category, clientActive, category == CategorieClient.LOCAL ? home : null));
        return comptes.saveAndFlush(Compte.invite(number, accountActive, RoleTechnique.CLIENT, client, null, null));
    }

    private void insertAdmin(String identifier, String state, String candidate, boolean active) {
        jdbc.update("insert into compte(identifiant,password_hash,etat_identifiants,actif,role,perimetre_admin)"
                + " values(?,?,?,?,'ADMIN','GLOBAL')", identifier, candidate, state, active);
    }

    private HttpResponse<String> login(String identifier) throws Exception {
        String body = json.writeValueAsString(Map.of("identifiant", identifier, "password", chosenPassword));
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertSafe(String body) {
        assertThat(body.contains(chosenPassword) || body.contains(hash)).isFalse();
        assertThat(body).doesNotContain("password", "passwordHash", "password_hash", "accessToken", "stackTrace");
    }

    private String schemaAt(String version) {
        String schema = "invited_" + UUID.randomUUID().toString().replace("-", "");
        flyway(schema, version).migrate();
        return schema;
    }

    private Flyway flyway(String schema, String version) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target(version).load();
    }
}
