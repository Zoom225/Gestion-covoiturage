package com.plateforme.covoiturage;

import com.plateforme.covoiturage.dto.request.CompteSearchRequest;
import com.plateforme.covoiturage.entity.Client;
import com.plateforme.covoiturage.entity.Compte;
import com.plateforme.covoiturage.entity.Zone;
import com.plateforme.covoiturage.enums.CategorieClient;
import com.plateforme.covoiturage.enums.PerimetreAdmin;
import com.plateforme.covoiturage.enums.RoleTechnique;
import com.plateforme.covoiturage.repository.ClientRepository;
import com.plateforme.covoiturage.repository.CompteRepository;
import com.plateforme.covoiturage.repository.ZoneRepository;
import com.plateforme.covoiturage.security.JwtProperties;
import com.plateforme.covoiturage.service.AuthService;
import com.plateforme.covoiturage.service.CompteService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class AdminCompteIntegrationTests {
    private static final String API = "/api/admin/comptes";
    private static final String PASSWORD = UUID.randomUUID().toString();
    private final HttpClient http = HttpClient.newHttpClient();
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired CompteRepository comptes;
    @Autowired ClientRepository clients;
    @Autowired ZoneRepository zones;
    @Autowired CompteService service;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired JwtEncoder encoder;
    @Autowired JwtProperties jwtProperties;
    private Zone home, other;
    private Compte global, local, otherAdmin, inactiveAdmin;
    private Compte vip, localClient, inactiveAccount, inactiveClient, away, vipUnzoned, occasionalUnzoned;
    private String hash;
    private final List<Compte> all = new ArrayList<>();

    @BeforeEach
    void fixturesInDisposablePostgresOnly() {
        // Datasource @ServiceConnection : uniquement le PostgreSQL éphémère de ces tests.
        jdbc.update("delete from compte");
        jdbc.update("delete from vehicule");
        jdbc.execute("truncate table client_numero_reservation");
        jdbc.update("delete from client");
        jdbc.update("delete from zone");
        all.clear();
        home = zones.saveAndFlush(new Zone("Test A"));
        other = zones.saveAndFlush(new Zone("Test B"));
        hash = passwords.encode(PASSWORD);
        global = admin("network@consultation.test", PerimetreAdmin.GLOBAL, null, true);
        local = admin("area-a@consultation.test", PerimetreAdmin.LOCAL, home, true);
        otherAdmin = admin("area-b@consultation.test", PerimetreAdmin.LOCAL, other, true);
        vip = client("V8101", CategorieClient.VIP, home, true, true);
        localClient = client("C8101", CategorieClient.LOCAL, home, true, true);
        inactiveAccount = client("O8101", CategorieClient.OCCASIONNEL, home, false, true);
        inactiveClient = client("O8102", CategorieClient.OCCASIONNEL, home, true, false);
        away = client("C8102", CategorieClient.LOCAL, other, true, true);
        vipUnzoned = client("V8102", CategorieClient.VIP, null, true, true);
        occasionalUnzoned = client("O8103", CategorieClient.OCCASIONNEL, null, true, true);
        inactiveAdmin = admin("inactive@consultation.test", PerimetreAdmin.GLOBAL, null, false);
    }

    @Test
    void globalReadsAllProfilesIncludingUnzonedAndDisabledAccounts() throws Exception {
        String token = token(global);
        var response = get(API, token);
        assertThat(response.statusCode()).isEqualTo(200);
        var page = json.readTree(response.body());
        assertThat(ids(page)).containsExactlyElementsOf(all.stream().map(Compte::getId).toList());
        assertThat(page.path("totalElements").asLong()).isEqualTo(all.size());
        assertThat(page.path("page").asInt()).isZero();
        assertThat(page.path("size").asInt()).isEqualTo(20);
        assertThat(page.path("totalPages").asInt()).isEqualTo(1);
        for (var account : List.of(vipUnzoned, occasionalUnzoned, inactiveAdmin, otherAdmin)) {
            assertThat(get(API + "/" + account.getId(), token).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void localReadsOnlyClientsOfItsZoneIncludingTheirDistinctStatuses() throws Exception {
        var page = page(API, token(local));
        assertThat(ids(page)).containsExactly(vip.getId(), localClient.getId(),
                inactiveAccount.getId(), inactiveClient.getId());
        assertThat(page.path("totalElements").asLong()).isEqualTo(4);
        for (var account : page.path("content")) {
            assertThat(account.path("role").asText()).isEqualTo("CLIENT");
            assertThat(account.path("zoneClientId").asLong()).isEqualTo(home.getId());
        }
    }

    @Test
    void localCannotReadAdministratorsEvenItselfOrOneInTheSameZone() throws Exception {
        String token = token(local);
        for (var account : List.of(global, local, otherAdmin, inactiveAdmin)) {
            error(get(API + "/" + account.getId(), token), 404, "NOT_FOUND");
        }
        assertThat(get("/api/auth/me", token).statusCode()).isEqualTo(200);
    }

    @Test
    void hiddenAndAbsentDetailsHaveIdenticalGenericErrors() throws Exception {
        String token = token(local);
        var absent = error(get(API + "/9223372036854775807", token), 404, "NOT_FOUND");
        for (var account : List.of(away, vipUnzoned, occasionalUnzoned, global)) {
            var hidden = error(get(API + "/" + account.getId(), token), 404, "NOT_FOUND");
            for (String field : List.of("status", "code", "message", "validationErrors")) {
                assertThat(hidden.path(field)).isEqualTo(absent.path(field));
            }
            assertThat(hidden.path("message").asText()).isEqualTo("Ressource introuvable");
        }
        error(get(API + "/9223372036854775807", token(global)), 404, "NOT_FOUND");
    }

    @Test
    void exactIdentifierSearchIgnoresCaseWithoutPartialMatchingOrWildcards() throws Exception {
        String token = token(global);
        assertThat(ids(page(API + "?identifiant=NETWORK%40CONSULTATION.TEST", token)))
                .containsExactly(global.getId());
        assertThat(ids(page(API + "?identifiant=v8101", token))).containsExactly(vip.getId());
        for (String search : List.of("v81", "%", "V8101 ", "absent")) {
            var result = page(API + "?identifiant=" + URLEncoder.encode(search, StandardCharsets.UTF_8), token);
            assertThat(ids(result)).isEmpty();
            assertThat(result.path("totalElements").asLong()).isZero();
        }
    }

    @Test
    void filtersIntersectEachOtherAndNeverWidenLocalScope() throws Exception {
        String token = token(local);
        assertThat(ids(page(API + "?role=CLIENT&categorie=LOCAL&zoneId=" + home.getId()
                + "&compteActif=true&clientActif=true", token))).containsExactly(localClient.getId());
        for (String query : List.of("?role=ADMIN", "?zoneId=" + other.getId(),
                "?identifiant=V8102", "?role=ADMIN&categorie=VIP")) {
            var result = page(API + query, token);
            assertThat(ids(result)).isEmpty();
            assertThat(result.path("totalElements").asLong()).isZero();
            assertThat(result.path("totalPages").asInt()).isZero();
        }
    }

    @Test
    void globalZoneFilterUsesTheApplicableAdministrativeOrClientZone() throws Exception {
        String token = token(global);
        assertThat(ids(page(API + "?zoneId=" + home.getId(), token))).containsExactly(
                local.getId(), vip.getId(), localClient.getId(), inactiveAccount.getId(), inactiveClient.getId());
        assertThat(ids(page(API + "?role=ADMIN&zoneId=" + other.getId(), token)))
                .containsExactly(otherAdmin.getId());
        assertThat(ids(page(API + "?role=CLIENT&zoneId=" + other.getId(), token)))
                .containsExactly(away.getId());
    }

    @Test
    void accountAndClientStatusFiltersRemainIndependent() throws Exception {
        String token = token(global);
        assertThat(ids(page(API + "?compteActif=false", token)))
                .containsExactly(inactiveAccount.getId(), inactiveAdmin.getId());
        assertThat(ids(page(API + "?clientActif=false&compteActif=true", token)))
                .containsExactly(inactiveClient.getId());
        assertThat(ids(page(API + "?role=ADMIN&clientActif=true", token))).isEmpty();
        assertThat(ids(page(API + "?role=ADMIN&categorie=LOCAL", token))).isEmpty();
    }

    @Test
    void paginationAndCountsUseTheSameLocalPredicateOnEveryPage() throws Exception {
        String token = token(local);
        var first = page(API + "?page=0&size=2", token);
        var second = page(API + "?page=1&size=2", token);
        var beyond = page(API + "?page=2&size=2", token);
        assertThat(ids(first)).containsExactly(vip.getId(), localClient.getId());
        assertThat(ids(second)).containsExactly(inactiveAccount.getId(), inactiveClient.getId());
        assertThat(ids(beyond)).isEmpty();
        for (var result : List.of(first, second, beyond)) {
            assertThat(result.path("totalElements").asLong()).isEqualTo(4);
            assertThat(result.path("totalPages").asInt()).isEqualTo(2);
        }
        assertThat(page(API + "?size=100", token).path("size").asInt()).isEqualTo(100);
    }

    @Test
    void defaultPageActuallyLimitsTwentyRowsInIncreasingAccountIdOrder() throws Exception {
        for (int i = 0; i < 21; i++) {
            client("O9" + String.format(Locale.ROOT, "%04d", i), CategorieClient.OCCASIONNEL, home, true, true);
        }
        var result = page(API, token(global));
        assertThat(ids(result)).containsExactlyElementsOf(all.stream().map(Compte::getId).limit(20).toList());
        assertThat(result.path("content").size()).isEqualTo(20);
        assertThat(result.path("totalElements").asLong()).isEqualTo(all.size());
        assertThat(result.path("totalPages").asInt()).isEqualTo(2);
    }

    @Test
    void addingHiddenRowsCannotChangeTheLocalPageOrItsCounters() throws Exception {
        String token = token(local);
        var before = page(API + "?size=2", token);
        client("O8998", CategorieClient.OCCASIONNEL, other, true, true);
        admin("hidden@consultation.test", PerimetreAdmin.LOCAL, home, true);
        assertThat(page(API + "?size=2", token)).isEqualTo(before);
    }

    @Test
    void everyClientCategoryIsDeniedBeforeTargetExistenceIsLookedUp() throws Exception {
        for (var account : List.of(vip, localClient, occasionalUnzoned)) {
            String token = token(account);
            for (String path : List.of(API, API + "/" + vip.getId(), API + "/9223372036854775807")) {
                error(get(path, token), 403, "FORBIDDEN");
            }
        }
    }

    @Test
    void absentMalformedExpiredJwtAndDisabledActorProduce401() throws Exception {
        for (String path : List.of(API, API + "/" + vip.getId())) {
            error(get(path, null), 401, "UNAUTHORIZED");
            error(get(path, "invalid"), 401, "UNAUTHORIZED");
        }
        var claims = JwtClaimsSet.builder().issuer(jwtProperties.issuer())
                .audience(List.of(jwtProperties.audience())).subject(global.getId().toString())
                .issuedAt(Instant.now().minusSeconds(300)).expiresAt(Instant.now().minusSeconds(120)).build();
        String expired = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        error(get(API, expired), 401, "UNAUTHORIZED");
        String token = token(global);
        jdbc.update("update compte set actif = false where id = ?", global.getId());
        error(get(API, token), 401, "UNAUTHORIZED");
    }

    @Test
    void anExistingJwtUsesTheCurrentZoneAndRoleFromPostgres() throws Exception {
        String token = token(local);
        jdbc.update("update compte set zone_admin_id = ? where id = ?", other.getId(), local.getId());
        assertThat(ids(page(API, token))).containsExactly(away.getId());
        error(get(API + "/" + vip.getId(), token), 404, "NOT_FOUND");
        var newClient = clients.saveAndFlush(new Client("O8999", CategorieClient.OCCASIONNEL, true, null));
        jdbc.update("update compte set role = 'CLIENT', client_id = ?, identifiant = ?, "
                        + "perimetre_admin = null, zone_admin_id = null where id = ?",
                newClient.getId(), newClient.getNumero(), local.getId());
        error(get(API, token), 403, "FORBIDDEN");
    }

    @Test
    void serviceEnforcesPermissionsEvenWithoutTheHttpRoleGate() {
        var actor = UsernamePasswordAuthenticationToken.authenticated(
                auth.resolvePrincipal(vip.getId().toString()), null, List.of());
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var search = new CompteSearchRequest(null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.search(actor, search)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.findById(actor, global.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.search(null, search)).isInstanceOf(AccessDeniedException.class);
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    @Test
    void listAndDetailLoadTheirRelationsWithoutNPlusOneQueries() throws Exception {
        String token = token(global);
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        assertThat(page(API + "?size=5", token).path("content").size()).isEqualTo(5);
        // Principal + sélection + count au maximum, indépendamment du nombre de lignes.
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
        assertThat(statistics.getEntityFetchCount()).isZero();
        statistics.clear();
        assertThat(get(API + "/" + localClient.getId(), token).statusCode()).isEqualTo(200);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
        assertThat(statistics.getEntityFetchCount()).isZero();
    }

    @Test
    void safeDtoNoStoreAndEffectiveStatusAreConsistentInListAndDetail() throws Exception {
        String token = token(global);
        var response = get(API, token);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertSafe(response.body());
        for (var item : json.readTree(response.body()).path("content")) {
            assertThat(json.convertValue(item, new TypeReference<Map<String, Object>>() {}).keySet()).containsExactlyInAnyOrder(
                    "compteId", "identifiant", "role", "clientId", "numeroClient", "categorie",
                    "zoneClientId", "perimetreAdmin", "zoneAdminId", "compteActif", "clientActif", "accesEffectif");
        }
        var admin = json.readTree(get(API + "/" + global.getId(), token).body());
        assertThat(admin.path("identifiant").asText()).isEqualTo(global.getIdentifiant());
        assertThat(admin.path("clientActif").isNull()).isTrue();
        for (var account : List.of(inactiveClient, inactiveAccount, inactiveAdmin)) {
            var detail = get(API + "/" + account.getId(), token);
            assertThat(detail.headers().firstValue("Cache-Control")).hasValue("no-store");
            assertSafe(detail.body());
            assertThat(json.readTree(detail.body()).path("accesEffectif").asBoolean()).isFalse();
        }
        var active = json.readTree(get(API + "/" + vip.getId(), token).body());
        assertThat(active.path("identifiant").asText()).isEqualTo(active.path("numeroClient").asText());
        assertThat(active.path("accesEffectif").asBoolean()).isTrue();
    }

    @Test
    void openApiDescribesBothGetEndpointsWithoutEntityOrCredentialSchemas() throws Exception {
        var spec = json.readTree(get("/v3/api-docs", null).body());
        var list = spec.path("paths").path(API).path("get");
        var detail = spec.path("paths").path(API + "/{id}").path("get");
        for (var operation : List.of(list, detail)) {
            assertThat(operation.path("responses").has("200")).isTrue();
            assertThat(operation.path("responses").has("400")).isTrue();
            assertThat(operation.path("responses").has("401")).isTrue();
            assertThat(operation.path("responses").has("403")).isTrue();
        }
        assertThat(detail.path("responses").has("404")).isTrue();
        assertSafe(spec.path("components").path("schemas").path("CompteAdministrationResponse").toString());
        assertThat(spec.path("components").path("schemas").has("Compte")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"?page=-1", "?size=0", "?size=101", "?page=x", "?size=x",
            "?role=VIP", "?categorie=ADMIN", "?zoneId=0", "?zoneId=x", "?compteActif=x",
            "?clientActif=x", "?page=2147483647"})
    void invalidQueryParametersProduceControlled400(String query) throws Exception {
        var response = get(API + query, token(global));
        assertThat(response.statusCode()).isEqualTo(400);
        assertSafe(response.body());
        assertThat(json.readTree(response.body()).path("validationErrors").isArray()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "not-an-id", "9223372036854775808"})
    void invalidDetailIdentifiersProduceControlled400(String id) throws Exception {
        var response = get(API + "/" + id, token(global));
        assertThat(response.statusCode()).isEqualTo(400);
        assertSafe(response.body());
    }

    @Test
    void oversizedSearchIdentifierIsRejectedWithoutEchoingIt() throws Exception {
        var response = get(API + "?identifiant=" + "x".repeat(101), token(global));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body().contains("x".repeat(101))).isFalse();
    }

    private List<Long> ids(JsonNode page) {
        var ids = new ArrayList<Long>();
        page.path("content").forEach(account -> ids.add(account.path("compteId").asLong()));
        return ids;
    }

    private Compte admin(String identifier, PerimetreAdmin scope, Zone zone, boolean active) {
        var result = comptes.saveAndFlush(new Compte(identifier, hash, active, RoleTechnique.ADMIN, null, scope, zone));
        all.add(result);
        return result;
    }

    private Compte client(String number, CategorieClient category, Zone zone, boolean accountActive, boolean clientActive) {
        var person = clients.saveAndFlush(new Client(number, category, clientActive, zone));
        var result = comptes.saveAndFlush(new Compte(number, hash, accountActive, RoleTechnique.CLIENT, person, null, null));
        all.add(result);
        return result;
    }

    private String token(Compte account) throws Exception {
        var body = json.writeValueAsString(Map.of("identifiant", account.getIdentifiant(), "password", PASSWORD));
        var request = HttpRequest.newBuilder(uri("/api/auth/login")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).path("accessToken").asText();
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(20)).GET();
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private JsonNode page(String path, String token) throws Exception {
        var response = get(path, token);
        assertThat(response.statusCode()).isEqualTo(200);
        assertSafe(response.body());
        return json.readTree(response.body());
    }

    private JsonNode error(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertSafe(response.body());
        var body = json.readTree(response.body());
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("code").asText()).isEqualTo(code);
        assertThat(body.has("timestamp")).isTrue();
        assertThat(body.has("path")).isTrue();
        assertThat(body.has("validationErrors")).isTrue();
        if (status == 401) assertThat(response.headers().firstValue("WWW-Authenticate")).hasValue("Bearer");
        return body;
    }

    private void assertSafe(String body) {
        assertThat(body.toLowerCase(Locale.ROOT)).doesNotContain(
                "password", "hash", "secret", "token", "stacktrace", "org.springframework", "hibernate");
        // N'afficher aucune valeur de credential, même si une assertion échoue.
        assertThat(body.contains(PASSWORD)).isFalse();
        assertThat(body.contains(hash)).isFalse();
    }
}
