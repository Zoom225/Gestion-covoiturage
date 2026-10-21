package com.plateforme.covoiturage;

import com.plateforme.covoiturage.entity.Client;
import com.plateforme.covoiturage.entity.Compte;
import com.plateforme.covoiturage.enums.CategorieClient;
import com.plateforme.covoiturage.enums.RoleTechnique;
import com.plateforme.covoiturage.repository.ClientRepository;
import com.plateforme.covoiturage.repository.CompteRepository;
import com.plateforme.covoiturage.service.ClientNumberGenerator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ClientNumberingIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired ClientNumberGenerator generator;
    @Autowired ClientRepository clients;
    @Autowired CompteRepository comptes;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DataSource dataSource;
    TransactionTemplate transaction;

    @BeforeEach
    @AfterEach
    void resetIsolatedFixtures() {
        transaction = new TransactionTemplate(transactionManager);
        // This datasource is the disposable Testcontainers service connection only.
        jdbc.update("delete from compte");
        jdbc.update("delete from vehicule");
        jdbc.execute("truncate table client_numero_reservation");
        jdbc.update("delete from client");
        jdbc.update("delete from zone");
        for (String prefix : new String[]{"v", "c", "o"}) {
            jdbc.execute("alter sequence client_numero_" + prefix + "_seq restart with 1");
        }
    }

    @Test
    void startsAtFourDigitsWithIndependentSequences() {
        assertThat(reserve(CategorieClient.VIP)).isEqualTo("V0001");
        assertThat(reserve(CategorieClient.VIP)).isEqualTo("V0002");
        assertThat(reserve(CategorieClient.LOCAL)).isEqualTo("C0001");
        assertThat(reserve(CategorieClient.OCCASIONNEL)).isEqualTo("O0001");
        assertThat(jdbc.queryForObject("select count(*) from client_numero_reservation", Integer.class)).isEqualTo(4);
    }

    @Test
    void growsPastFourDigitsWithoutTruncationForEveryCategory() {
        var categories = CategorieClient.values();
        for (var category : categories) {
            String prefix = switch (category) {
                case VIP -> "V";
                case LOCAL -> "C";
                case OCCASIONNEL -> "O";
            };
            jdbc.queryForObject("select setval(?::regclass, 9998, true)", Long.class,
                    "client_numero_" + prefix.toLowerCase(java.util.Locale.ROOT) + "_seq");
            assertThat(reserve(category)).isEqualTo(prefix + "9999");
            assertThat(reserve(category)).isEqualTo(prefix + "10000");
        }
    }

    @Test
    void requiresTheCallersTransaction() {
        assertThatThrownBy(() -> generator.reserve(CategorieClient.VIP))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void commitsDistinctClientAllocationsFromConcurrentTransactions() throws Exception {
        int total = 32;
        var ready = new CountDownLatch(8);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < total; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                    return transaction.execute(status -> {
                        String number = generator.reserve(CategorieClient.VIP);
                        clients.saveAndFlush(new Client(number, CategorieClient.VIP, true, null));
                        return number;
                    });
                }));
            }
            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var numbers = new HashSet<String>();
            for (var future : futures) numbers.add(future.get(45, TimeUnit.SECONDS));
            assertThat(numbers).hasSize(total);
        } finally {
            start.countDown();
        }
        assertThat(jdbc.queryForObject("select count(*) from client", Integer.class)).isEqualTo(total);
        assertThat(jdbc.queryForObject(
                "select count(*) from client_numero_reservation where client_id is not null",
                Integer.class)).isEqualTo(total);
    }

    @Test
    void rollsBackClientAccountAndReservationButDoesNotReuseConsumedSequenceValue() {
        assertThatThrownBy(() -> transaction.execute(status -> {
            String number = generator.reserve(CategorieClient.VIP);
            Client client = clients.saveAndFlush(new Client(number, CategorieClient.VIP, true, null));
            comptes.saveAndFlush(new Compte("legacy-test-login", passwords.encode(UUID.randomUUID().toString()),
                    true, RoleTechnique.CLIENT, client, null, null));
            throw new IllegalStateException("Injected failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from client", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from compte", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from client_numero_reservation", Integer.class)).isZero();
        assertThat(reserve(CategorieClient.VIP)).isEqualTo("V0002");
    }

    @Test
    void retainsOldNumbersAndRejectsReassignmentEvenToTheirFormerOwner() {
        Long clientId = transaction.execute(status -> {
            String number = generator.reserve(CategorieClient.VIP);
            return clients.saveAndFlush(new Client(number, CategorieClient.VIP, true, null)).getId();
        });
        transaction.executeWithoutResult(status -> {
            String replacement = generator.reserve(CategorieClient.VIP);
            // Exercise SQL history protection, not a category-transition API.
            jdbc.update("update client set numero = ? where id = ?", replacement, clientId);
        });
        assertThat(jdbc.queryForObject(
                "select retire_le is not null from client_numero_reservation where numero = 'V0001'",
                Boolean.class)).isTrue();
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                jdbc.update("update client set numero = 'V0001' where id = ?", clientId)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                clients.saveAndFlush(new Client("V0001", CategorieClient.VIP, true, null))))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Even an accidental counter rewind cannot reallocate a committed historical number.
        jdbc.queryForObject("select setval('client_numero_v_seq', 1, false)", Long.class);
        assertThat(reserve(CategorieClient.VIP)).isEqualTo("V0003");
        assertThat(jdbc.queryForObject("select numero from client where id = ?", String.class, clientId))
                .isEqualTo("V0002");
    }

    @Test
    void capturesJpaWritersAndSkipsTheirNumbers() {
        Client client = clients.saveAndFlush(new Client("V0001", CategorieClient.VIP, true, null));
        assertThat(reserve(CategorieClient.VIP)).isEqualTo("V0002");
        assertThat(jdbc.queryForObject(
                "select client_id from client_numero_reservation where numero = 'V0001'", Long.class))
                .isEqualTo(client.getId());
    }

    @Test
    void doesNotLetAnotherTransactionClaimAnUnassignedReservation() {
        String number = reserve(CategorieClient.VIP);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                clients.saveAndFlush(new Client(number, CategorieClient.VIP, true, null))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from client", Integer.class)).isZero();
    }

    @Test
    void preventsDeletionAndMutationOfAllocationHistory() {
        reserve(CategorieClient.VIP);
        assertThatThrownBy(() -> jdbc.update("delete from client_numero_reservation"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "update client_numero_reservation set numero = 'V9999' where numero = 'V0001'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "update client_numero_reservation set reservation_tx = pg_current_xact_id()"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select numero from client_numero_reservation", String.class))
                .isEqualTo("V0001");
    }

    @Test
    void sequenceExhaustionCannotWrapAndReallocate() {
        jdbc.queryForObject("select setval('client_numero_v_seq', 9223372036854775807, true)", Long.class);
        assertThatThrownBy(() -> reserve(CategorieClient.VIP)).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("select count(*) from client_numero_reservation", Integer.class)).isZero();
    }

    @Test
    void upgradesV2WithoutChangingAccountsNumbersOrStableIds() {
        String schema = newSchema();
        flyway(schema, "2").migrate();
        jdbc.update("insert into " + schema + ".zone(id,nom) values (10,'Existing zone')");
        jdbc.update("insert into " + schema + ".client(id,numero,categorie,actif,zone_id) values "
                + "(31,'V0042','VIP',true,null),(32,'C10001','LOCAL',true,10),(33,'O0007','OCCASIONNEL',false,null)");
        String hash = passwords.encode(UUID.randomUUID().toString());
        jdbc.update("insert into " + schema + ".compte(id,identifiant,password_hash,actif,role,client_id)"
                + " values (17,'existing-client-alias',?,true,'CLIENT',31)", hash);
        jdbc.update("insert into " + schema + ".compte(id,identifiant,password_hash,actif,role,perimetre_admin)"
                + " values (18,'existing-admin-alias',?,true,'ADMIN','GLOBAL')", hash);
        assertThat(flyway(schema, "3").migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway(schema, "3").validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForObject("select identifiant from " + schema + ".compte where id=17", String.class))
                .isEqualTo("existing-client-alias");
        assertThat(jdbc.queryForObject("select identifiant from " + schema + ".compte where id=18", String.class))
                .isEqualTo("existing-admin-alias");
        // Compare as a boolean so an assertion cannot print a BCrypt hash.
        assertThat(hash.equals(jdbc.queryForObject(
                "select password_hash from " + schema + ".compte where id=17", String.class))).isTrue();
        assertThat(jdbc.queryForObject("select client_id from " + schema + ".compte where id=17", Long.class))
                .isEqualTo(31L);
        assertThat(jdbc.queryForList("select numero from " + schema + ".client order by id", String.class))
                .containsExactly("V0042", "C10001", "O0007");
        assertThat(jdbc.queryForObject("select count(*) from " + schema
                + ".client_numero_reservation where origine='MIGRATION'", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select " + schema + ".reserve_client_number('VIP')", String.class))
                .isEqualTo("V0043");
        assertThat(jdbc.queryForObject("select " + schema + ".reserve_client_number('LOCAL')", String.class))
                .isEqualTo("C10002");
        assertThat(jdbc.queryForObject("select " + schema + ".reserve_client_number('OCCASIONNEL')", String.class))
                .isEqualTo("O0008");
        assertThat(flyway(schema, "3").migrate().migrationsExecuted).isZero();
    }

    @Test
    void failsBeforeImportingUnmappedOrOverflowingLegacyNumbers() {
        for (String legacy : new String[]{"legacy-number", "v0001", "V9223372036854775807"}) {
            String schema = newSchema();
            flyway(schema, "2").migrate();
            jdbc.update("insert into " + schema
                    + ".client(numero,categorie,actif) values (?,'VIP',true)", legacy);
            assertThatThrownBy(() -> flyway(schema, "3").migrate()).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("select numero from " + schema + ".client", String.class))
                    .isEqualTo(legacy);
            assertThat(jdbc.queryForObject("select count(*) from information_schema.tables"
                    + " where table_schema=? and table_name='client_numero_reservation'", Integer.class, schema))
                    .isZero();
        }
    }

    private String reserve(CategorieClient category) {
        return transaction.execute(status -> generator.reserve(category));
    }

    private String newSchema() {
        return "numbering_" + UUID.randomUUID().toString().replace("-", "");
    }

    private Flyway flyway(String schema, String version) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                .target(version).load();
    }
}
