package com.plateforme.covoiturage;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "driveconnect.security.cors.allowed-origins=https://frontend.example.test")
@Import({TestcontainersConfiguration.class, SecurityIntegrationTests.AdminProbe.class})
class SecurityIntegrationTests {
    private static final String PASSWORD = "test-password-only";
    private final HttpClient http = HttpClient.newHttpClient();
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired JwtProperties jwtProperties;
    @Autowired CompteRepository comptes;
    @Autowired ClientRepository clients;
    @Autowired ZoneRepository zones;
    @Autowired JdbcTemplate jdbc;
    private Compte clientAccount;
    private Compte localAdmin;
    private Compte globalAdmin;
    private Client client;
    private Zone home;
    private Zone other;

    @BeforeEach
    void fixtures() {
        // Base éphémère Testcontainers uniquement ; aucune écriture dans une base locale.
        jdbc.update("delete from compte");
        jdbc.update("delete from vehicule");
        jdbc.update("delete from client");
        jdbc.update("delete from zone");
        home = zones.saveAndFlush(new Zone("Zone locale"));
        other = zones.saveAndFlush(new Zone("Autre zone"));
        client = clients.saveAndFlush(new Client("V0001", CategorieClient.VIP, true, home));
        String hash = passwords.encode(PASSWORD);
        clientAccount = comptes.saveAndFlush(new Compte("client", hash, true, RoleTechnique.CLIENT, client, null, null));
        localAdmin = comptes.saveAndFlush(new Compte("local-admin", hash, true, RoleTechnique.ADMIN, null, PerimetreAdmin.LOCAL, home));
        globalAdmin = comptes.saveAndFlush(new Compte("global-admin", hash, true, RoleTechnique.ADMIN, null, PerimetreAdmin.GLOBAL, null));
    }

    @Test
    void bcryptIsSaltedAndVerifiesOnlyTheRightPassword() {
        String first = passwords.encode(PASSWORD);
        String second = passwords.encode(PASSWORD);
        assertThat(first).startsWith("$2a$").isNotEqualTo(second).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, first)).isTrue();
        assertThat(passwords.matches("incorrect", first)).isFalse();
        assertThat(comptes.findById(clientAccount.getId()).orElseThrow().getPasswordHash()).isNotEqualTo(PASSWORD);
    }

    @Test
    void loginIssuesSignedTokenAndOwnProfileWithoutSecretsOrSession() throws Exception {
        var response = login("client", PASSWORD);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        JsonNode body = mapper.readTree(response.body());
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("expiresIn").asLong()).isEqualTo(900);
        String token = body.get("accessToken").asText();
        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(clientAccount.getId().toString());
        assertThat(jwt.getClaims()).doesNotContainKeys("password", "passwordHash", "role", "zoneId", "categorie");
        var profile = request("GET", "/api/auth/me", null, token);
        assertThat(profile.statusCode()).isEqualTo(200);
        var json = mapper.readTree(profile.body());
        assertThat(json.get("compteId").asLong()).isEqualTo(clientAccount.getId());
        assertThat(json.get("clientId").asLong()).isEqualTo(client.getId());
        assertThat(json.get("categorie").asText()).isEqualTo("VIP");
        assertThat(json.get("role").asText()).isEqualTo("CLIENT");
        assertThat(profile.body()).doesNotContain("password", "hash", PASSWORD);
        assertError(request("GET", "/api/auth/me", null, null), 401, "UNAUTHORIZED");
    }

    @Test
    void unknownUserWrongPasswordAndDisabledAccountHaveSameError() throws Exception {
        var unknown = login("unknown", PASSWORD);
        var wrong = login("client", "incorrect");
        jdbc.update("update compte set actif = false where id = ?", clientAccount.getId());
        var disabled = login("client", PASSWORD);
        for (var response : List.of(unknown, wrong, disabled)) {
            assertError(response, 401, "UNAUTHORIZED");
            assertThat(mapper.readTree(response.body()).get("message").asText())
                    .isEqualTo("Authentification requise ou invalide");
            assertThat(response.body()).doesNotContain("unknown", "incorrect", PASSWORD);
        }
    }

    @Test
    void disabledClientCannotLoginOrReuseToken() throws Exception {
        String token = accessToken("client");
        jdbc.update("update client set actif = false where id = ?", client.getId());
        assertError(login("client", PASSWORD), 401, "UNAUTHORIZED");
        assertError(request("GET", "/api/auth/me", null, token), 401, "UNAUTHORIZED");
    }

    @Test
    void disabledOrDeletedAccountCannotReuseToken() throws Exception {
        String token = accessToken("client");
        jdbc.update("update compte set actif = false where id = ?", clientAccount.getId());
        assertError(request("GET", "/api/auth/me", null, token), 401, "UNAUTHORIZED");
        jdbc.update("delete from compte where id = ?", clientAccount.getId());
        assertError(request("GET", "/api/auth/me", null, token), 401, "UNAUTHORIZED");
    }

    @Test
    void invalidAndBlankJsonProduceSafeValidationErrors() throws Exception {
        assertError(request("POST", "/api/auth/login", "{", null), 400, "INVALID_REQUEST");
        var response = request("POST", "/api/auth/login", "{\"identifiant\":\"\",\"password\":\"\"}", null);
        assertError(response, 400, "VALIDATION_ERROR");
        assertThat(mapper.readTree(response.body()).get("validationErrors").size()).isEqualTo(2);
        assertError(request("POST", "/api/auth/login", "{}", null), 400, "VALIDATION_ERROR");
    }

    @Test
    void oversizedAndUnicodePasswordsDoNotCauseInternalErrors() throws Exception {
        assertError(login("client", "x".repeat(73)), 400, "VALIDATION_ERROR");
        assertError(login("client", "é".repeat(40)), 401, "UNAUTHORIZED");
    }

    @Test
    void frontendFieldsCannotElevateRoleOrChangeIdentityOrZone() throws Exception {
        var loginBody = Map.of("identifiant", "client", "password", PASSWORD, "role", "ADMIN",
                "clientId", -1, "zoneId", other.getId(), "categorie", "VIP");
        var response = request("POST", "/api/auth/login", mapper.writeValueAsString(loginBody), null);
        assertThat(response.statusCode()).isEqualTo(200);
        String token = mapper.readTree(response.body()).get("accessToken").asText();
        assertError(request("GET", "/api/admin/probe", null, token), 403, "FORBIDDEN");
        var profile = request("GET", "/api/auth/me?clientId=-1&role=ADMIN&zoneId=-1", null, token);
        assertThat(mapper.readTree(profile.body()).get("compteId").asLong()).isEqualTo(clientAccount.getId());
        assertThat(mapper.readTree(profile.body()).get("zoneClientId").asLong()).isEqualTo(home.getId());
    }

    @Test
    void clientCategoryNeverGrantsAdminRole() throws Exception {
        String token = accessToken("client");
        for (String category : List.of("VIP", "LOCAL", "OCCASIONNEL")) {
            jdbc.update("update client set categorie = ? where id = ?", category, client.getId());
            assertError(request("GET", "/api/admin/probe", null, token), 403, "FORBIDDEN");
        }
        assertThat(request("GET", "/api/admin/probe", null, accessToken("global-admin")).statusCode()).isEqualTo(200);
    }

    @Test
    void localAndGlobalAdminScopesAreEnforcedByMethodSecurity() throws Exception {
        String local = accessToken("local-admin");
        assertThat(request("GET", "/api/admin/probe/zones/" + home.getId(), null, local).statusCode()).isEqualTo(200);
        assertError(request("GET", "/api/admin/probe/zones/" + other.getId(), null, local), 403, "FORBIDDEN");
        String global = accessToken("global-admin");
        assertThat(request("GET", "/api/admin/probe/zones/" + other.getId(), null, global).statusCode()).isEqualTo(200);
        jdbc.update("update compte set zone_admin_id = ? where id = ?", other.getId(), localAdmin.getId());
        assertError(request("GET", "/api/admin/probe/zones/" + home.getId(), null, local), 403, "FORBIDDEN");
        assertThat(request("GET", "/api/admin/probe/zones/" + other.getId(), null, local).statusCode()).isEqualTo(200);
    }

    @Test
    void roleChangesInDatabaseApplyToAnAlreadyIssuedToken() throws Exception {
        String token = accessToken("client");
        assertError(request("GET", "/api/admin/probe", null, token), 403, "FORBIDDEN");
        jdbc.update("update compte set role = 'ADMIN', client_id = null, perimetre_admin = 'GLOBAL' where id = ?", clientAccount.getId());
        assertThat(request("GET", "/api/admin/probe", null, token).statusCode()).isEqualTo(200);
        jdbc.update("update compte set role = 'CLIENT', client_id = ?, perimetre_admin = null where id = ?", client.getId(), clientAccount.getId());
        assertError(request("GET", "/api/admin/probe", null, token), 403, "FORBIDDEN");
    }

    @Test
    void missingMalformedAndTamperedTokensAreRejected() throws Exception {
        assertError(request("GET", "/api/auth/me", null, null), 401, "UNAUTHORIZED");
        assertError(request("GET", "/api/auth/me", null, "not-a-jwt"), 401, "UNAUTHORIZED");
        String token = accessToken("client");
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"999999\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + parts[2];
        assertError(request("GET", "/api/auth/me", null, tampered), 401, "UNAUTHORIZED");
    }

    @Test
    void expiredFutureWrongIssuerWrongAudienceAndMissingExpiryAreRejected() throws Exception {
        Instant now = Instant.now();
        var valid = claims().build();
        for (var invalid : List.of(
                JwtClaimsSet.from(valid).issuedAt(now.minusSeconds(120)).expiresAt(now.minusSeconds(1)).build(),
                JwtClaimsSet.from(valid).notBefore(now.plusSeconds(300)).build(),
                JwtClaimsSet.from(valid).issuer("wrong-issuer").build(),
                JwtClaimsSet.from(valid).audience(List.of("wrong-audience")).build(),
                JwtClaimsSet.from(valid).claims(values -> values.remove("exp")).build())) {
            assertError(request("GET", "/api/auth/me", null, signed(invalid)), 401, "UNAUTHORIZED");
        }
    }

    @Test
    void evenSignedRoleAndZoneClaimsAreIgnored() throws Exception {
        String token = signed(claims().claim("roles", List.of("ADMIN")).claim("role", "ADMIN")
                .claim("zoneId", other.getId()).build());
        assertError(request("GET", "/api/admin/probe", null, token), 403, "FORBIDDEN");
        assertThat(mapper.readTree(request("GET", "/api/auth/me", null, token).body()).get("zoneClientId").asLong())
                .isEqualTo(home.getId());
    }

    @Test
    void wrongSignatureAndWrongAlgorithmAreRejected() throws Exception {
        byte[] foreignKey = new byte[64];
        new java.security.SecureRandom().nextBytes(foreignKey);
        var foreignEncoder = NimbusJwtEncoder.withSecretKey(new javax.crypto.spec.SecretKeySpec(foreignKey, "HmacSHA256"))
                .algorithm(MacAlgorithm.HS256).build();
        String foreignToken = foreignEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims().build())).getTokenValue();
        assertError(request("GET", "/api/auth/me", null, foreignToken), 401, "UNAUTHORIZED");

        byte[] key = java.util.Base64.getDecoder().decode(jwtProperties.secret());
        var hs384 = NimbusJwtEncoder.withSecretKey(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA384"))
                .algorithm(MacAlgorithm.HS384).build();
        String wrongAlgorithm = hs384.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS384).build(), claims().build())).getTokenValue();
        assertError(request("GET", "/api/auth/me", null, wrongAlgorithm), 401, "UNAUTHORIZED");
    }

    @Test
    void unknownOrMalformedSubjectIsUnauthorized() throws Exception {
        assertError(request("GET", "/api/auth/me", null, signed(claims().subject("999999999").build())), 401, "UNAUTHORIZED");
        assertError(request("GET", "/api/auth/me", null, signed(claims().subject("not-an-id").build())), 401, "UNAUTHORIZED");
    }

    @Test
    void corsAllowsOnlyConfiguredOrigin() throws Exception {
        var allowed = HttpRequest.newBuilder(uri("/api/auth/me")).method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "https://frontend.example.test").header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization").build();
        var response = http.send(allowed, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).hasValue("https://frontend.example.test");
        var denied = HttpRequest.newBuilder(uri("/api/auth/me")).method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "https://untrusted.example.test").header("Access-Control-Request-Method", "GET").build();
        assertError(http.send(denied, HttpResponse.BodyHandlers.ofString()), 403, "FORBIDDEN");
    }

    @Test
    void databaseRejectsInvalidAccountProfilesAndPlaintextPasswords() {
        assertThatThrownBy(() -> jdbc.update("insert into compte(identifiant,password_hash,actif,role,client_id) values ('invalid','plaintext',true,'CLIENT',?)", client.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> comptes.saveAndFlush(new Compte("invalid", passwords.encode(PASSWORD), true,
                RoleTechnique.ADMIN, null, PerimetreAdmin.LOCAL, null)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> comptes.saveAndFlush(new Compte("invalid", passwords.encode(PASSWORD), true,
                RoleTechnique.CLIENT, null, null, null)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder().issuer(jwtProperties.issuer()).audience(List.of(jwtProperties.audience()))
                .subject(clientAccount.getId().toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600));
    }

    private String signed(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private HttpResponse<String> login(String identifiant, String password) throws Exception {
        return request("POST", "/api/auth/login", mapper.writeValueAsString(Map.of("identifiant", identifiant, "password", password)), null);
    }

    private String accessToken(String identifiant) throws Exception {
        var response = login(identifiant, PASSWORD);
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body()).get("accessToken").asText();
    }

    private HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        var error = mapper.readTree(response.body());
        assertThat(error.get("status").asInt()).isEqualTo(status);
        assertThat(error.get("code").asText()).isEqualTo(code);
        assertThat(error.has("timestamp")).isTrue();
        assertThat(error.has("path")).isTrue();
        assertThat(error.has("validationErrors")).isTrue();
        assertThat(response.body()).doesNotContain("passwordHash", "stackTrace", "org.springframework");
        if (status == 401) assertThat(response.headers().firstValue("WWW-Authenticate")).hasValue("Bearer");
    }

    // Endpoints réservés aux tests, absents du code de production.
    @RestController
    static class AdminProbe {
        @GetMapping("/api/admin/probe")
        String admin() { return "ok"; }

        @GetMapping("/api/admin/probe/zones/{zoneId}")
        @PreAuthorize("@adminAuthorization.canManageZone(authentication, #zoneId)")
        String zone(@PathVariable Long zoneId) { return "ok"; }
    }
}
