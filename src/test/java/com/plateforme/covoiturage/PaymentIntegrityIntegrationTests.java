package com.plateforme.covoiturage;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL constraints; every fixture has its own disposable schema. */
@Testcontainers
class PaymentIntegrityIntegrationTests {
    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16.6-alpine"));

    enum Kind {
        PLACE("paiement_place", "reservation_id", "reservation"),
        DETTE("paiement_dette", "dette_id", "dette_trajet");

        final String payments, reference, parent;
        Kind(String payments, String reference, String parent) {
            this.payments = payments; this.reference = reference; this.parent = parent;
        }
        long first(Fixture f) { return this == PLACE ? f.bookingA() : f.debtA(); }
        long second(Fixture f) { return this == PLACE ? f.bookingB() : f.debtB(); }
    }

    record Fixture(String schema, long zone, long vehicle, long client,
                   long bookingA, long bookingB, long debtA, long debtB) {}

    Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).defaultSchema(schema).target(target).load();
    }

    Connection connection(String schema) throws SQLException {
        var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        connection.setSchema(schema);
        connection.setAutoCommit(false);
        return connection;
    }

    long scalar(Connection c, String sql) throws SQLException {
        try (var statement = c.createStatement(); var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue(); return rows.getLong(1);
        }
    }

    void execute(Connection c, String sql) throws SQLException {
        try (var statement = c.createStatement()) { statement.execute(sql); }
    }

    long trip(Connection c, long zone, long vehicle, long client, int days) throws SQLException {
        return scalar(c, "insert into trajet(zone_id,vehicule_id,organisateur_id,depart,arrivee,type) values("
                + zone + "," + vehicle + "," + client + ",now()+interval '" + days + " days',"
                + "now()+interval '" + days + " days 1 hour','PUBLIC') returning id");
    }

    void insertPayment(Connection c, Kind kind, long parent) throws SQLException {
        execute(c, "insert into " + kind.payments + "(" + kind.reference
                + ",cle_idempotence,montant,mode,valide_le) values(" + parent + ",'"
                + UUID.randomUUID() + "',20,'SIMULATION',now())");
    }

    Fixture fixture(String target) throws SQLException {
        String schema = "integrity_" + UUID.randomUUID().toString().replace("-", "");
        flyway(schema, target).migrate();
        try (var c = connection(schema)) {
            long zone = scalar(c, "insert into zone(nom,fuseau_horaire,ouverte_occasionnels)"
                    + " values('Disposable integrity','Europe/Brussels',true) returning id");
            long vehicle = scalar(c, "insert into vehicule(zone_id) values(" + zone + ") returning id");
            long a = scalar(c, "insert into client(numero,categorie,actif)"
                    + " values(reserve_client_number('OCCASIONNEL'),'OCCASIONNEL',true) returning id");
            long b = scalar(c, "insert into client(numero,categorie,actif)"
                    + " values(reserve_client_number('OCCASIONNEL'),'OCCASIONNEL',true) returning id");
            long firstTrip = trip(c, zone, vehicle, a, 2), secondTrip = trip(c, zone, vehicle, a, 3);
            long bookingA = scalar(c, "insert into reservation(trajet_id,client_id,statut) values("
                    + firstTrip + "," + a + ",'CONFIRMEE') returning id");
            long bookingB = scalar(c, "insert into reservation(trajet_id,client_id,statut) values("
                    + firstTrip + "," + b + ",'CONFIRMEE') returning id");
            insertPayment(c, Kind.PLACE, bookingA); insertPayment(c, Kind.PLACE, bookingB);
            long debtA = scalar(c, "insert into dette_trajet(trajet_id,client_id,regle) values("
                    + firstTrip + "," + a + ",20) returning id");
            long debtB = scalar(c, "insert into dette_trajet(trajet_id,client_id,regle) values("
                    + secondTrip + "," + a + ",20) returning id");
            insertPayment(c, Kind.DETTE, debtA); insertPayment(c, Kind.DETTE, debtB);
            c.commit();
            return new Fixture(schema, zone, vehicle, a, bookingA, bookingB, debtA, debtB);
        }
    }

    List<String> snapshot(Fixture f, boolean history) throws SQLException {
        var result = new ArrayList<String>();
        var tables = history ? List.of("flyway_schema_history")
                : List.of("trajet", "reservation", "paiement_place", "dette_trajet", "paiement_dette");
        try (var c = connection(f.schema())) {
            for (String table : tables) {
                String query = history ? "select row_to_json(t)::text from " + table
                        + " t where version::int<=12 order by installed_rank"
                        : "select row_to_json(t)::text from " + table + " t order by id";
                try (var s = c.createStatement(); var rows = s.executeQuery(query)) {
                    while (rows.next()) result.add(table + ":" + rows.getString(1));
                }
            }
        }
        return result;
    }

    long inconsistencies(Fixture f, Kind kind) throws SQLException {
        try (var c = connection(f.schema())) {
            return scalar(c, kind == Kind.PLACE
                    ? "select count(*) from reservation r where (statut='CONFIRMEE')<>"
                    + "exists(select 1 from paiement_place p where p.reservation_id=r.id)"
                    : "select count(*) from dette_trajet d where regle<>"
                    + "(select coalesce(sum(montant),0) from paiement_dette p where p.dette_id=d.id)");
        }
    }

    @FunctionalInterface interface SqlWork { void run(Connection c) throws SQLException; }

    void rejects(Fixture f, String sqlState, SqlWork operation) throws SQLException {
        var before = snapshot(f, false);
        try (var c = connection(f.schema())) {
            assertThatThrownBy(() -> { operation.run(c); c.commit(); })
                    .isInstanceOf(SQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo(sqlState));
            c.rollback();
        }
        assertThat(snapshot(f, false)).isEqualTo(before);
    }

    void abandonOldReference(Connection c, Fixture f, Kind kind) throws SQLException {
        execute(c, "delete from " + kind.payments + " where " + kind.reference + "=" + kind.second(f));
        execute(c, "update " + kind.payments + " set " + kind.reference + "=" + kind.second(f)
                + " where " + kind.reference + "=" + kind.first(f));
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void v12CounterexampleIsReproducedAndUpgradeRefusesCorruptedHistory(Kind kind) throws Exception {
        var f = fixture("12");
        try (var c = connection(f.schema())) { abandonOldReference(c, f, kind); c.commit(); }
        assertThat(inconsistencies(f, kind)).isEqualTo(1);
        var rows = snapshot(f, false); var history = snapshot(f, true);
        assertThatThrownBy(() -> flyway(f.schema(), "13").migrate()).isInstanceOf(FlywayException.class);
        assertThat(snapshot(f, false)).isEqualTo(rows);
        assertThat(snapshot(f, true)).isEqualTo(history);
        try (var c = connection(f.schema())) {
            assertThat(scalar(c, "select max(version::int) from flyway_schema_history")).isEqualTo(12);
        }
    }

    @Test void v12UpgradePreservesRowsChecksumsAndDeferredTriggers() throws Exception {
        var f = fixture("12"); var rows = snapshot(f, false); var history = snapshot(f, true);
        var upgrade = flyway(f.schema(), "13");
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(upgrade.validateWithResult().validationSuccessful).isTrue();
        assertThat(upgrade.migrate().migrationsExecuted).isZero();
        assertThat(snapshot(f, false)).isEqualTo(rows);
        assertThat(snapshot(f, true)).isEqualTo(history);
        try (var c = connection(f.schema())) {
            assertThat(scalar(c, "select count(*) from pg_trigger t join pg_class r on r.oid=t.tgrelid"
                    + " join pg_namespace n on n.oid=r.relnamespace where n.nspname='" + f.schema()
                    + "' and t.tgname in ('trg_confirmation_paid','trg_payment_confirmed',"
                    + "'trg_debt_balance','trg_settlement_balance') and t.tgdeferrable and t.tginitdeferred")).isEqualTo(4);
        }
        assertThat(inconsistencies(f, Kind.PLACE)).isZero();
        assertThat(inconsistencies(f, Kind.DETTE)).isZero();
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void oldReferencesCannotBeAbandoned(Kind kind) throws Exception {
        var f = fixture("13");
        rejects(f, "23514", c -> abandonOldReference(c, f, kind));
    }

    void makeSecondBookingPending(Fixture f) throws SQLException {
        try (var c = connection(f.schema())) {
            execute(c, "delete from paiement_place where reservation_id=" + f.bookingB());
            execute(c, "update reservation set statut='EN_ATTENTE' where id=" + f.bookingB());
            c.commit();
        }
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void newReferencesMustAlsoRemainConsistent(Kind kind) throws Exception {
        var f = fixture("13");
        if (kind == Kind.PLACE) makeSecondBookingPending(f);
        rejects(f, "23514", c -> {
            execute(c, kind == Kind.PLACE
                    ? "update reservation set statut='EN_ATTENTE' where id=" + f.bookingA()
                    : "update dette_trajet set regle=0 where id=" + f.debtA());
            execute(c, "update " + kind.payments + " set " + kind.reference + "=" + kind.second(f)
                    + " where " + kind.reference + "=" + kind.first(f));
        });
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void insertedPaymentsRequireMatchingParent(Kind kind) throws Exception {
        var f = fixture("13");
        if (kind == Kind.PLACE) makeSecondBookingPending(f);
        rejects(f, "23514", c -> insertPayment(c, kind, kind.second(f)));
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void deletedPaymentsCannotLeaveTheirParentInconsistent(Kind kind) throws Exception {
        var f = fixture("13");
        rejects(f, "23514", c -> execute(c, "delete from " + kind.payments
                + " where " + kind.reference + "=" + kind.first(f)));
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void parentInsertsCannotInventPaymentProof(Kind kind) throws Exception {
        var f = fixture("13");
        rejects(f, "23514", c -> {
            long trip = trip(c, f.zone(), f.vehicle(), f.client(), 4);
            execute(c, kind == Kind.PLACE
                    ? "insert into reservation(trajet_id,client_id,statut) values(" + trip + "," + f.client() + ",'CONFIRMEE')"
                    : "insert into dette_trajet(trajet_id,client_id,regle) values(" + trip + "," + f.client() + ",20)");
        });
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void parentUpdatesCannotDiscardPaymentProof(Kind kind) throws Exception {
        var f = fixture("13");
        rejects(f, "23514", c -> execute(c, kind == Kind.PLACE
                ? "update reservation set statut='EN_ATTENTE' where id=" + f.bookingA()
                : "update dette_trajet set regle=30 where id=" + f.debtA()));
    }

    @ParameterizedTest @CsvSource({"PLACE,true", "PLACE,false", "DETTE,true", "DETTE,false"})
    void coherentReassignmentIsCheckedAtCommitInEitherOrder(Kind kind, boolean paymentFirst) throws Exception {
        // Constraint test only: no cancellation/refund endpoint or business permission is introduced.
        var f = fixture("13");
        try (var c = connection(f.schema())) {
            if (kind == Kind.PLACE) execute(c, "delete from paiement_place where reservation_id=" + f.bookingB());
            SqlWork move = db -> execute(db, "update " + kind.payments + " set " + kind.reference
                    + "=" + kind.second(f) + " where " + kind.reference + "=" + kind.first(f));
            SqlWork balance = db -> {
                if (kind == Kind.PLACE) execute(db, "update reservation set statut='EN_ATTENTE' where id=" + f.bookingA());
                else {
                    execute(db, "update dette_trajet set regle=0 where id=" + f.debtA());
                    execute(db, "update dette_trajet set regle=40 where id=" + f.debtB());
                }
            };
            if (paymentFirst) { move.run(c); balance.run(c); }
            else { balance.run(c); move.run(c); }
            c.commit();
        }
        assertThat(inconsistencies(f, kind)).isZero();
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void paymentAndParentCanBeRemovedInOneCoherentTransaction(Kind kind) throws Exception {
        var f = fixture("13");
        try (var c = connection(f.schema())) {
            execute(c, "delete from " + kind.payments + " where " + kind.reference + "=" + kind.first(f));
            execute(c, "delete from " + kind.parent + " where id=" + kind.first(f));
            c.commit();
        }
        assertThat(inconsistencies(f, kind)).isZero();
    }

    @ParameterizedTest @EnumSource(Kind.class)
    void updatingTheSameReferencePreservesValidHistory(Kind kind) throws Exception {
        var f = fixture("13"); var before = snapshot(f, false);
        try (var c = connection(f.schema())) {
            execute(c, "update " + kind.payments + " set " + kind.reference + "=" + kind.reference);
            c.commit();
        }
        assertThat(snapshot(f, false)).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings={"price", "idempotency", "foreignKey", "debtLimit", "debtAmount", "mode"})
    void existingPaymentConstraintsRemainActive(String constraint) throws Exception {
        var f = fixture("13");
        String state = switch (constraint) { case "idempotency" -> "23505"; case "foreignKey" -> "23503"; default -> "23514"; };
        rejects(f, state, c -> execute(c, switch (constraint) {
            case "price" -> "update paiement_place set montant=21 where reservation_id=" + f.bookingA();
            case "idempotency" -> "update paiement_place set cle_idempotence=(select cle_idempotence from paiement_place where reservation_id=" + f.bookingB() + ") where reservation_id=" + f.bookingA();
            case "foreignKey" -> "update paiement_place set reservation_id=9223372036854775807 where reservation_id=" + f.bookingA();
            case "debtLimit" -> "update dette_trajet set regle=81 where id=" + f.debtA();
            case "debtAmount" -> "update paiement_dette set montant=30 where dette_id=" + f.debtA();
            default -> "update paiement_place set mode='BANK' where reservation_id=" + f.bookingA();
        }));
    }
}
