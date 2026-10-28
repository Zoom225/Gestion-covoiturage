package com.plateforme.covoiturage;

import com.plateforme.covoiturage.entity.Trajet;
import com.plateforme.covoiturage.mapper.TrajetMapper;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class TrajetGeographyMigrationTests extends JourneyIntegrationSupport {
    @Autowired PostgreSQLContainer postgres;
    @Autowired EntityManagerFactory entityManagers;
    @Autowired TrajetMapper mapper;
    static final long LEGACY_ID = 1_000_000;

    record Fixture(String schema, long zone, long vehicle, long client) {}
    @FunctionalInterface interface SqlWork { void run(Connection c) throws SQLException; }

    Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target(target).load();
    }

    Connection connection(String schema) throws SQLException {
        // A separate JDBC connection prevents search_path changes leaking into the application pool.
        var c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        c.setSchema(schema);
        c.setAutoCommit(false);
        return c;
    }

    void sql(Connection c, String sql) throws SQLException {
        try (var statement = c.createStatement()) { statement.execute(sql); }
    }

    long scalar(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement(); var rows = s.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getLong(1);
        }
    }

    Fixture historicalFixture() throws SQLException {
        var f = new Fixture("locations_" + UUID.randomUUID().toString().replace("-", ""),
                home.getId(), vehicle.getId(), local.getClient().getId());
        flyway(f.schema(), "13").migrate();
        try (var c = connection(f.schema())) {
            sql(c, "insert into zone(id,nom,fuseau_horaire) values(" + f.zone() + ",'Zone historique','Europe/Brussels')");
            sql(c, "insert into vehicule(id,zone_id) values(" + f.vehicle() + "," + f.zone() + ")");
            sql(c, "insert into client(id,numero,categorie,actif,zone_id) values(" + f.client()
                    + ",reserve_client_number('LOCAL'),'LOCAL',true," + f.zone() + ")");
            sql(c, "insert into trajet(id,zone_id,vehicule_id,organisateur_id,depart,arrivee,type) values("
                    + LEGACY_ID + "," + f.zone() + "," + f.vehicle() + "," + f.client()
                    + ",now()+interval '2 days',now()+interval '2 days 1 hour','PRIVE')");
            sql(c, "insert into reservation(id,trajet_id,client_id,statut) values("
                    + LEGACY_ID + "," + LEGACY_ID + "," + f.client() + ",'CONFIRMEE')");
            sql(c, "insert into paiement_place(reservation_id,cle_idempotence,montant,mode,valide_le) values("
                    + LEGACY_ID + ",'" + UUID.randomUUID() + "',20,'SIMULATION',now())");
            sql(c, "insert into dette_trajet(id,trajet_id,client_id,regle) values("
                    + LEGACY_ID + "," + LEGACY_ID + "," + f.client() + ",20)");
            sql(c, "insert into paiement_dette(dette_id,cle_idempotence,montant,mode,valide_le) values("
                    + LEGACY_ID + ",'" + UUID.randomUUID() + "',20,'SIMULATION',now())");
            c.commit();
        }
        return f;
    }

    List<String> snapshot(Fixture f, boolean history) throws SQLException {
        var result = new ArrayList<String>();
        var tables = history ? List.of("flyway_schema_history")
                : List.of("zone", "vehicule", "client", "trajet", "reservation",
                        "paiement_place", "dette_trajet", "paiement_dette");
        try (var c = connection(f.schema())) {
            for (String table : tables) {
                String query = history ? "select row_to_json(t)::text from " + table
                        + " t where version::int<=13 order by installed_rank"
                        : "select (to_jsonb(t)-'origine'-'destination')::text from " + table + " t order by id";
                try (var s = c.createStatement(); var rows = s.executeQuery(query)) {
                    while (rows.next()) result.add(table + ":" + rows.getString(1));
                }
            }
        }
        return result;
    }

    void insertTrip(Connection c, Fixture f, String origine, String destination) throws SQLException {
        try (var s = c.prepareStatement("insert into trajet(zone_id,vehicule_id,organisateur_id,depart,arrivee,type,origine,destination)"
                + " values(?,?,?,now()+interval '5 days',now()+interval '5 days 1 hour','PUBLIC',?,?)")) {
            s.setLong(1, f.zone()); s.setLong(2, f.vehicle()); s.setLong(3, f.client());
            s.setString(4, origine); s.setString(5, destination);
            s.executeUpdate();
        }
    }

    void rejects(Fixture f, String state, SqlWork operation) throws SQLException {
        var before = snapshot(f, false);
        try (var c = connection(f.schema())) {
            assertThatThrownBy(() -> { operation.run(c); c.commit(); })
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo(state));
            c.rollback();
        }
        assertThat(snapshot(f, false)).isEqualTo(before);
    }

    @Test void v13UpgradePreservesExistingTripsPaymentsDebtsAndChecksums() throws Exception {
        var f = historicalFixture();
        var rows = snapshot(f, false);
        var history = snapshot(f, true);
        var upgrade = flyway(f.schema(), "14");
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("14");
        assertThat(upgrade.validateWithResult().validationSuccessful).isTrue();
        assertThat(upgrade.migrate().migrationsExecuted).isZero();
        assertThat(snapshot(f, false)).isEqualTo(rows);
        assertThat(snapshot(f, true)).isEqualTo(history);
        try (var c = connection(f.schema())) {
            assertThat(scalar(c, "select count(*) from trajet where origine is null and destination is null")).isEqualTo(1);
            assertThat(scalar(c, "select count(*) from pg_trigger t join pg_class r on r.oid=t.tgrelid"
                    + " join pg_namespace n on n.oid=r.relnamespace where n.nspname='" + f.schema()
                    + "' and t.tgname in ('trg_confirmation_paid','trg_payment_confirmed',"
                    + "'trg_debt_balance','trg_settlement_balance') and t.tgdeferrable and t.tginitdeferred")).isEqualTo(4);
        }
    }

    @Test void historicalUnknownLocationsHydrateThroughJpaAndSerializeAsNull() throws Exception {
        var f = historicalFixture();
        // L'entité actuelle doit être hydratée depuis le schéma courant ; V14 conserve les lieux inconnus.
        flyway(f.schema(), "21").migrate();
        try (var em = entityManagers.createEntityManager()) {
            var historical = (Trajet) em.createNativeQuery("select * from " + f.schema() + ".trajet", Trajet.class)
                    .getSingleResult();
            var response = mapper.trajet(historical);
            var payload = json.readTree(json.writeValueAsString(response));
            assertThat(payload.path("trajetId").asLong()).isEqualTo(LEGACY_ID);
            assertThat(payload.path("origine").isNull()).isTrue();
            assertThat(payload.path("destination").isNull()).isTrue();
            assertThat(payload.path("traiteJ1Le").isNull()).isTrue();
            assertThat(payload.path("verificationJ1Requise").asBoolean()).isFalse();
        }
    }

    static Stream<Arguments> invalidSqlLocations() {
        return Stream.of(
                Arguments.of(null, null, "23514"),
                Arguments.of(null, "Gare B", "23514"),
                Arguments.of("Gare A", null, "23514"),
                Arguments.of("", "Gare B", "23514"),
                Arguments.of("Gare A", " \t\n", "23514"),
                Arguments.of("\u2003", "Gare B", "23514"),
                Arguments.of("x".repeat(256), "Gare B", "22001"),
                Arguments.of("Gare A", "x".repeat(256), "22001"));
    }

    @ParameterizedTest @MethodSource("invalidSqlLocations")
    void postgresRejectsInvalidNewLocationsWithoutChangingHistory(String origine, String destination, String state) throws Exception {
        var f = historicalFixture();
        flyway(f.schema(), "14").migrate();
        rejects(f, state, c -> insertTrip(c, f, origine, destination));
    }

    @Test void historicalUpdatesRemainPossibleButEnrichmentMustBeCompleteAndCannotBeErased() throws Exception {
        var f = historicalFixture();
        flyway(f.schema(), "14").migrate();
        try (var c = connection(f.schema())) {
            sql(c, "update trajet set type='PUBLIC',traite_j1_le=now() where id=" + LEGACY_ID);
            sql(c, "update trajet set origine=origine where id=" + LEGACY_ID);
            c.commit();
        }
        rejects(f, "23514", c -> sql(c, "update trajet set origine='Gare A' where id=" + LEGACY_ID));
        rejects(f, "23514", c -> sql(c, "update trajet set origine=' ',destination='Gare B' where id=" + LEGACY_ID));
        try (var c = connection(f.schema())) {
            sql(c, "update trajet set origine='Gare A',destination='Gare B' where id=" + LEGACY_ID);
            c.commit();
        }
        for (String assignment : List.of("origine=null", "destination=null",
                "origine=null,destination=null", "destination=' '")) {
            rejects(f, "23514", c -> sql(c, "update trajet set " + assignment + " where id=" + LEGACY_ID));
        }
        try (var c = connection(f.schema())) {
            assertThat(scalar(c, "select count(*) from trajet where origine='Gare A' and destination='Gare B'"
                    + " and type='PUBLIC' and traite_j1_le is not null")).isEqualTo(1);
        }
    }

    @Test void postgresAcceptsNewTripsWithBothLocationsAtTheTechnicalLimit() throws Exception {
        var f = historicalFixture();
        flyway(f.schema(), "14").migrate();
        try (var c = connection(f.schema())) {
            insertTrip(c, f, "É".repeat(255), "É".repeat(255));
            c.commit();
            assertThat(scalar(c, "select count(*) from trajet where length(origine)=255 and length(destination)=255")).isEqualTo(1);
        }
    }
}
