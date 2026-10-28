package com.plateforme.covoiturage;

import com.plateforme.covoiturage.entity.Compte;
import com.plateforme.covoiturage.enums.*;
import com.plateforme.covoiturage.scheduling.TripCompletionScheduler;
import com.plateforme.covoiturage.service.TrajetService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

class J1HistoryIntegrationTests extends ReportingIntegrationSupport {
    private static final Instant ARRIVAL = NOW.plusSeconds(176400);
    @Autowired TrajetService completion;
    @Autowired PostgreSQLContainer postgres;
    Compte fourth;

    private long privateTrip(int confirmed) throws Exception {
        return privateTrip(confirmed, NOW.plusSeconds(172800));
    }

    private long privateTrip(int confirmed, Instant departure) throws Exception {
        var response = call("POST", "/api/trajets", local, request(departure, departure.plusSeconds(3600), TypeTrajet.PRIVE));
        assertThat(response.statusCode()).isEqualTo(201);
        long trip = read(response).path("trajetId").asLong();
        fourth = account("O980150", CategorieClient.OCCASIONNEL, null);
        for (var participant : List.of(vip, occasional, fourth)) {
            assertThat(call("POST", "/api/trajets/" + trip + "/participants", local,
                    Map.of("clientId", participant.getClient().getId())).statusCode()).isEqualTo(201);
        }
        var participants = List.of(local, vip, occasional, fourth);
        for (int index = 0; index < confirmed; index++) paySeat(trip, participants.get(index));
        return trip;
    }

    private void cancelVip(long trip, Instant at) throws Exception {
        clock.set(at);
        var response = call("POST", "/api/reservations/" + booking(trip, vip) + "/annulation", vip, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(read(response).path("annuleLe").asText()).isEqualTo(at.toString());
    }

    private List<String> money() {
        var snapshot = new ArrayList<String>();
        for (String table : List.of("paiement_place", "paiement_dette", "dette_trajet")) {
            snapshot.addAll(jdbc.queryForList("select to_jsonb(p)::text from " + table + " p order by id", String.class));
        }
        return snapshot;
    }

    @Test void successfulCheckIsRecordedAndLaterCancellationCannotCreateAPenalty() throws Exception {
        long trip = privateTrip(4);
        clock.set(deadline().minusSeconds(1));
        assertThat(j1.catchUp()).isZero();
        assertThat(trips.findById(trip).orElseThrow().getTraiteJ1Le()).isNull();
        clock.set(deadline());
        assertThat(j1.catchUp()).isZero();
        assertThat(trips.findById(trip).orElseThrow().getTraiteJ1Le()).isEqualTo(deadline());
        assertThat(trips.findById(trip).orElseThrow().getType()).isEqualTo(TypeTrajet.PRIVE);
        assertThat(debts.count()).isZero();

        cancelVip(trip, deadline().plusSeconds(60));
        assertThat(j1.catchUp()).isZero();
        var before = money();
        clock.set(ARRIVAL);
        new TripCompletionScheduler(completion).process();

        var result = trips.findById(trip).orElseThrow();
        assertThat(result.getStatut()).isEqualTo(StatutTrajet.REALISE);
        assertThat(result.getType()).isEqualTo(TypeTrajet.PRIVE);
        assertThat(result.getTraiteJ1Le()).isEqualTo(deadline());
        assertThat(result.isVerificationJ1Requise()).isFalse();
        assertThat(debts.count()).isZero();
        assertThat(money()).isEqualTo(before);
    }

    @Test void restartRecoversSuccessfulJ1EvenWhenThereAreOnlyThreeConfirmationsNow() throws Exception {
        long trip = privateTrip(4);
        cancelVip(trip, deadline().plusSeconds(60));
        assertThat(reservations.countByTrajetIdAndStatut(trip, StatutReservation.CONFIRMEE)).isEqualTo(3);
        var before = money();
        Instant restart = ARRIVAL.plusSeconds(3 * 86400);
        clock.set(restart);

        new TripCompletionScheduler(completion).process();

        var result = trips.findById(trip).orElseThrow();
        assertThat(result.getStatut()).isEqualTo(StatutTrajet.REALISE);
        assertThat(result.getType()).isEqualTo(TypeTrajet.PRIVE);
        assertThat(result.getTraiteJ1Le()).isEqualTo(restart);
        assertThat(debts.count()).isZero();
        assertThat(money()).isEqualTo(before);
        assertThat(j1.catchUp()).isZero();
    }

    @Test void restartRecoversDuePenaltyEvenWhenTheTripHasBecomeFullAfterJ1() throws Exception {
        long trip = privateTrip(3);
        clock.set(deadline().plusSeconds(60));
        paySeat(trip, fourth);
        assertThat(trips.findById(trip).orElseThrow().getStatut()).isEqualTo(StatutTrajet.COMPLET);
        var payments = jdbc.queryForList("select to_jsonb(p)::text from paiement_place p order by id", String.class);
        clock.set(ARRIVAL.plusSeconds(86400));

        new TripCompletionScheduler(completion).process();

        var result = trips.findById(trip).orElseThrow();
        assertThat(result.getStatut()).isEqualTo(StatutTrajet.REALISE);
        assertThat(result.getType()).isEqualTo(TypeTrajet.PUBLIC);
        assertThat(result.getTraiteJ1Le()).isEqualTo(clock.instant());
        assertThat(debts.count()).isEqualTo(1);
        assertThat(debts.findAll().getFirst().getMontant()).isEqualByComparingTo("80.00");
        assertThat(jdbc.queryForList("select to_jsonb(p)::text from paiement_place p order by id", String.class)).isEqualTo(payments);
        var beforeRepeat = money();
        new TripCompletionScheduler(completion).process();
        assertThat(money()).isEqualTo(beforeRepeat);
    }

    @ParameterizedTest @ValueSource(longs = {-1, 0, 1})
    void catchUpUsesTheCancellationDateRelativeToTheJ1Boundary(long secondsAfterDeadline) throws Exception {
        var brussels = ZoneId.of("Europe/Brussels");
        Instant departure = NOW.atZone(brussels).toLocalDate().plusDays(2).atTime(0, 5).atZone(brussels).toInstant();
        Instant boundary = departure.atZone(brussels).toLocalDate().minusDays(1).atTime(0, 15).atZone(brussels).toInstant();
        long trip = privateTrip(4, departure);
        cancelVip(trip, boundary.plusSeconds(secondsAfterDeadline));
        assertThat(seatPayments.findByReservationId(booking(trip, vip)).orElseThrow().getRembourseLe()).isNull();
        clock.set(departure.plusSeconds(3600));

        new TripCompletionScheduler(completion).process();

        assertThat(trips.findById(trip).orElseThrow().getStatut()).isEqualTo(StatutTrajet.REALISE);
        assertThat(debts.count()).isEqualTo(secondsAfterDeadline <= 0 ? 1 : 0);
        assertThat(trips.findById(trip).orElseThrow().getType())
                .isEqualTo(secondsAfterDeadline <= 0 ? TypeTrajet.PUBLIC : TypeTrajet.PRIVE);
    }

    @Test void undatedOldPaidCancellationIsFlaggedWithoutDebtOrCompletion() throws Exception {
        long trip = privateTrip(4);
        long oldBooking = booking(trip, vip);
        jdbc.update("update reservation set statut='ANNULEE' where id=?", oldBooking);
        var before = money();
        clock.set(ARRIVAL.plusSeconds(7 * 86400));

        new TripCompletionScheduler(completion).process();
        new TripCompletionScheduler(completion).process();

        var result = trips.findById(trip).orElseThrow();
        assertThat(result.getType()).isEqualTo(TypeTrajet.PRIVE);
        assertThat(result.getStatut()).isEqualTo(StatutTrajet.PLANIFIE);
        assertThat(result.getTraiteJ1Le()).isNull();
        assertThat(result.isVerificationJ1Requise()).isTrue();
        assertThat(reservations.findById(oldBooking).orElseThrow().getAnnuleLe()).isNull();
        assertThat(money()).isEqualTo(before);
        var response = read(call("GET", "/api/trajets/" + trip, local, null));
        assertThat(response.path("verificationJ1Requise").asBoolean()).isTrue();
        assertThat(response.path("traiteJ1Le").isNull()).isTrue();
    }

    @Test void undatedUnpaidCancellationCannotAffectTheHistoricalConfirmedCount() throws Exception {
        long trip = privateTrip(1);
        long oldBooking = booking(trip, vip);
        jdbc.update("update reservation set statut='ANNULEE' where id=?", oldBooking);
        clock.set(ARRIVAL);

        new TripCompletionScheduler(completion).process();

        assertThat(trips.findById(trip).orElseThrow().getStatut()).isEqualTo(StatutTrajet.REALISE);
        assertThat(trips.findById(trip).orElseThrow().isVerificationJ1Requise()).isFalse();
        assertThat(debts.count()).isEqualTo(1);
        assertThat(reservations.findById(oldBooking).orElseThrow().getAnnuleLe()).isNull();
        assertThat(reservations.findById(booking(trip, fourth)).orElseThrow().getAnnuleLe()).isEqualTo(ARRIVAL);
    }

    @Test void oldPaymentAfterDeadlineDoesNotCreateUncertaintyAboutAnEarlierConfirmation() throws Exception {
        long trip = privateTrip(0);
        clock.set(deadline().plusSeconds(60));
        paySeat(trip, vip);
        jdbc.update("update reservation set statut='ANNULEE' where id=?", booking(trip, vip));
        paySeat(trip, occasional);
        clock.set(ARRIVAL);

        new TripCompletionScheduler(completion).process();

        assertThat(debts.count()).isEqualTo(1);
        assertThat(trips.findById(trip).orElseThrow().getStatut()).isEqualTo(StatutTrajet.REALISE);
        assertThat(trips.findById(trip).orElseThrow().isVerificationJ1Requise()).isFalse();
    }

    @Test void missingJ1IsStillProcessedWhenTheTripHasNoConfirmedPlaceToComplete() throws Exception {
        long trip = privateTrip(0);
        clock.set(ARRIVAL);

        assertThat(completion.terminerTrajetsArrives()).isZero();

        assertThat(trips.findById(trip).orElseThrow().getStatut()).isEqualTo(StatutTrajet.PLANIFIE);
        assertThat(trips.findById(trip).orElseThrow().getType()).isEqualTo(TypeTrajet.PUBLIC);
        assertThat(debts.count()).isEqualTo(1);
    }

    @Test void anExistingPartiallyPaidDebtIsNeverModifiedOrCreatedAgain() throws Exception {
        long trip = privateTrip(3);
        jdbc.update("insert into dette_trajet(trajet_id,client_id) values(?,?)", trip, local.getClient().getId());
        long debt = jdbc.queryForObject("select id from dette_trajet where trajet_id=?", Long.class, trip);
        payDebt(debt, local, "20.00");
        var before = money();
        clock.set(ARRIVAL);

        new TripCompletionScheduler(completion).process();
        new TripCompletionScheduler(completion).process();

        assertThat(money()).isEqualTo(before);
        assertThat(trips.findById(trip).orElseThrow().getStatut()).isEqualTo(StatutTrajet.REALISE);
    }

    @Test void concurrentCatchUpAndCompletionCreateOneDebt() throws Exception {
        long trip = privateTrip(3);
        clock.set(ARRIVAL);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var first = pool.submit(() -> { start.await(); return completion.terminerTrajetsArrives(); });
            var second = pool.submit(() -> { start.await(); return completion.terminerTrajetsArrives(); });
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(0, 1);
        }
        assertThat(debts.count()).isEqualTo(1);
        assertThat(trips.findById(trip).orElseThrow().getStatut()).isEqualTo(StatutTrajet.REALISE);
    }

    @Test void failedJ1CannotBeBypassedByCompletionAndIsRetriedNextPass() throws Exception {
        long trip = privateTrip(3);
        clock.set(ARRIVAL);
        jdbc.execute("create function reject_history_debt() returns trigger language plpgsql as $$ begin raise exception 'test rollback'; end $$");
        jdbc.execute("create trigger reject_history_debt before insert on dette_trajet for each row execute function reject_history_debt()");
        try {
            assertThat(completion.terminerTrajetsArrives()).isZero();
            var result = trips.findById(trip).orElseThrow();
            assertThat(result.getType()).isEqualTo(TypeTrajet.PRIVE);
            assertThat(result.getTraiteJ1Le()).isNull();
            assertThat(result.getStatut()).isEqualTo(StatutTrajet.PLANIFIE);
            assertThat(reservations.findById(booking(trip, fourth)).orElseThrow().getStatut()).isEqualTo(StatutReservation.EN_ATTENTE);
            assertThat(debts.count()).isZero();
        } finally {
            jdbc.execute("drop trigger reject_history_debt on dette_trajet");
            jdbc.execute("drop function reject_history_debt()");
        }
        assertThat(completion.terminerTrajetsArrives()).isEqualTo(1);
        assertThat(debts.count()).isEqualTo(1);
    }

    @Test void v21PreservesExistingDatesPaymentsRefundsAndDebtsWithoutInventingCancellationDates() throws Exception {
        String schema = "j1_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("20").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connection.setSchema(schema);
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("insert into zone(id,nom) values(1,'Zone')");
                statement.execute("insert into vehicule(id,zone_id) values(1,1)");
                statement.execute("insert into client(id,numero,categorie,actif,zone_id) values(1,reserve_client_number('LOCAL'),'LOCAL',true,1)");
                statement.execute("insert into trajet(id,zone_id,vehicule_id,organisateur_id,depart,arrivee,type,origine,destination,traite_j1_le) " +
                        "values(1,1,1,1,now()+interval '2 days',now()+interval '2 days 1 hour','PUBLIC','A','B',now())");
                statement.execute("insert into reservation(id,trajet_id,client_id,statut) values(1,1,1,'CONFIRMEE'),(2,1,1,'ANNULEE')");
                for (int id : List.of(1, 2)) statement.execute("insert into paiement_place(id,reservation_id,cle_idempotence,montant,mode,valide_le,rembourse_le) " +
                        "values(" + id + "," + id + ",'" + UUID.randomUUID() + "',20,'SIMULATION',now()," + (id == 2 ? "now()" : "null") + ")");
                statement.execute("insert into dette_trajet(id,trajet_id,client_id,montant) values(1,1,1,80)");
            }
            connection.commit();
        }
        var before = schemaHistory(schema);
        var upgrade = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("21").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(upgrade.migrate().migrationsExecuted).isZero();
        assertThat(upgrade.validateWithResult().validationSuccessful).isTrue();
        assertThat(schemaHistory(schema)).isEqualTo(before);
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connection.setSchema(schema);
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("select annule_le from reservation where id=2")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject(1)).isNull();
            }
        }
    }

    private List<String> schemaHistory(String schema) throws Exception {
        var history = new ArrayList<String>();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connection.setSchema(schema);
            for (String table : List.of("trajet", "reservation", "paiement_place", "dette_trajet")) {
                try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                        "select (to_jsonb(p)-'annule_le'-'verification_j1_requise')::text from " + table + " p order by id")) {
                    while (rows.next()) history.add(table + ":" + rows.getString(1));
                }
            }
        }
        return history;
    }
}
