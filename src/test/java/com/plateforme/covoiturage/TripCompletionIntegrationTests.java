package com.plateforme.covoiturage;

import com.plateforme.covoiturage.enums.*;
import com.plateforme.covoiturage.scheduling.TripCompletionScheduler;
import com.plateforme.covoiturage.service.SensitiveOperationJournal;
import com.plateforme.covoiturage.service.TrajetService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TripCompletionIntegrationTests extends ReportingIntegrationSupport {
    private static final Instant ARRIVAL = NOW.plusSeconds(176400);
    @Autowired TrajetService service;
    @Autowired PostgreSQLContainer postgres;
    @MockitoSpyBean SensitiveOperationJournal journal;

    private String status(long trip) {
        return jdbc.queryForObject("select statut from trajet where id=?", String.class, trip);
    }

    private List<String> financialHistory() {
        var history = new java.util.ArrayList<String>();
        for (String table : List.of("paiement_place", "paiement_dette", "dette_trajet")) {
            history.addAll(jdbc.queryForList("select to_jsonb(p)::text from " + table + " p order by id", String.class));
        }
        return history;
    }

    @ParameterizedTest @ValueSource(longs = {-1, 0, 1})
    void plannedTripCompletesAtArrivalOrAfterButNeverBefore(long secondsAfterArrival) throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        paySeat(trip, local);
        clock.set(ARRIVAL.plusSeconds(secondsAfterArrival));

        assertThat(service.terminerTrajetsArrives()).isEqualTo(secondsAfterArrival < 0 ? 0 : 1);
        assertThat(status(trip)).isEqualTo(secondsAfterArrival < 0 ? "PLANIFIE" : "REALISE");
        assertThat(reservations.findById(booking(trip, local)).orElseThrow().getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
        var response = call("GET", "/api/trajets/" + trip, local, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(read(response).path("statut").asText()).isEqualTo(status(trip));
    }

    @ParameterizedTest @EnumSource(TypeTrajet.class)
    void fullTripCompletesAndKeepsFourConfirmedReservationsAndPayments(TypeTrajet type) throws Exception {
        long trip = create(local, type);
        var fourth = account("O980100", CategorieClient.OCCASIONNEL, null);
        for (var participant : List.of(vip, occasional, fourth)) {
            if (type == TypeTrajet.PUBLIC) reserve(trip, participant);
            else assertThat(call("POST", "/api/trajets/" + trip + "/participants", local,
                    java.util.Map.of("clientId", participant.getClient().getId())).statusCode()).isEqualTo(201);
        }
        for (var participant : List.of(local, vip, occasional, fourth)) paySeat(trip, participant);
        assertThat(status(trip)).isEqualTo("COMPLET");
        var moneyBefore = financialHistory();
        clock.set(ARRIVAL.plusSeconds(1));

        assertThat(service.terminerTrajetsArrives()).isEqualTo(1);

        assertThat(status(trip)).isEqualTo("REALISE");
        assertThat(reservations.countByTrajetIdAndStatut(trip, StatutReservation.CONFIRMEE)).isEqualTo(4);
        assertThat(financialHistory()).isEqualTo(moneyBefore);
    }

    @Test void cancelledTripIsNeverCompletedOrModified() throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        long waiting = reserve(trip, vip);
        paySeat(trip, local);
        jdbc.update("update trajet set statut='ANNULE' where id=?", trip);
        var moneyBefore = financialHistory();
        clock.set(ARRIVAL.plusSeconds(1));

        assertThat(service.terminerTrajetsArrives()).isZero();

        assertThat(status(trip)).isEqualTo("ANNULE");
        assertThat(reservations.findById(waiting).orElseThrow().getStatut()).isEqualTo(StatutReservation.EN_ATTENTE);
        assertThat(financialHistory()).isEqualTo(moneyBefore);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void tripWithoutActiveConfirmationIsNotCompletedEvenWithAnOldPayment(boolean cancelledPayment) throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        long waiting = reserve(trip, vip);
        if (cancelledPayment) {
            paySeat(trip, local);
            assertThat(call("POST", "/api/reservations/" + booking(trip, local) + "/annulation", local, null).statusCode()).isEqualTo(200);
        }
        var moneyBefore = financialHistory();
        clock.set(ARRIVAL.plusSeconds(1));

        assertThat(service.terminerTrajetsArrives()).isZero();

        assertThat(status(trip)).isEqualTo("PLANIFIE");
        assertThat(reservations.findById(waiting).orElseThrow().getStatut()).isEqualTo(StatutReservation.EN_ATTENTE);
        assertThat(financialHistory()).isEqualTo(moneyBefore);
    }

    @Test void confirmedParticipantIsEnoughAndPendingOrganizerIsCancelledAndKeptInHistory() throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        long organizer = booking(trip, local);
        long confirmed = reserve(trip, vip);
        long waiting = reserve(trip, occasional);
        paySeat(trip, vip);
        long rowsBefore = reservations.count();
        clock.set(ARRIVAL);

        assertThat(service.terminerTrajetsArrives()).isEqualTo(1);

        assertThat(status(trip)).isEqualTo("REALISE");
        assertThat(reservations.count()).isEqualTo(rowsBefore);
        assertThat(reservations.findById(confirmed).orElseThrow().getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
        for (long id : List.of(organizer, waiting)) {
            assertThat(reservations.findById(id).orElseThrow().getStatut()).isEqualTo(StatutReservation.ANNULEE);
            assertThat(reservations.findById(id).orElseThrow().getAnnuleLe()).isEqualTo(ARRIVAL);
        }
        var ownHistory = read(call("GET", "/api/reservations/me", local, null)).path("content").get(0);
        assertThat(ownHistory.path("reservationId").asLong()).isEqualTo(organizer);
        assertThat(ownHistory.path("statut").asText()).isEqualTo("ANNULEE");
        assertThat(ownHistory.path("annuleLe").asText()).isEqualTo(ARRIVAL.toString());
    }

    @Test void schedulerRepeatedOrRecreatedDoesNotProcessACompletedTripTwice() throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        paySeat(trip, local);
        clock.set(ARRIVAL);

        new TripCompletionScheduler(service).process();
        new TripCompletionScheduler(service).process();

        assertThat(status(trip)).isEqualTo("REALISE");
        assertThat(service.terminerTrajetsArrives()).isZero();
        verify(journal, times(1)).record(SensitiveOperationJournal.Action.TRAJET_REALISE, null,
                SensitiveOperationJournal.Resource.TRAJET, trip);
    }

    @Test void firstSchedulerPassCatchesAnArrivalMissedForSevenDays() throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        paySeat(trip, local);
        clock.set(ARRIVAL.plusSeconds(7 * 86400));
        assertThat(status(trip)).isEqualTo("PLANIFIE");

        new TripCompletionScheduler(service).process();

        assertThat(status(trip)).isEqualTo("REALISE");
    }

    @Test void completionKeepsPaymentsRefundsAndPartiallyPaidDebtExactlyUnchanged() throws Exception {
        long trip = create(local, TypeTrajet.PRIVE);
        for (var participant : List.of(vip, occasional)) {
            assertThat(call("POST", "/api/trajets/" + trip + "/participants", local,
                    java.util.Map.of("clientId", participant.getClient().getId())).statusCode()).isEqualTo(201);
        }
        long cancelled = booking(trip, vip);
        paySeat(trip, vip);
        paySeat(trip, occasional);
        assertThat(call("POST", "/api/reservations/" + cancelled + "/annulation", vip, null).statusCode()).isEqualTo(200);
        assertThat(seatPayments.findByReservationId(cancelled).orElseThrow().getRembourseLe()).isEqualTo(NOW);
        clock.set(deadline());
        assertThat(j1.catchUp()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select type from trajet where id=?", String.class, trip)).isEqualTo("PUBLIC");
        long debt = jdbc.queryForObject("select id from dette_trajet where trajet_id=?", Long.class, trip);
        payDebt(debt, local, "20.00");
        var moneyBefore = financialHistory();
        clock.set(ARRIVAL);

        assertThat(service.terminerTrajetsArrives()).isEqualTo(1);

        assertThat(status(trip)).isEqualTo("REALISE");
        assertThat(financialHistory()).isEqualTo(moneyBefore);
        assertThat(reservations.findById(cancelled).orElseThrow().getStatut()).isEqualTo(StatutReservation.ANNULEE);
        assertThat(j1.catchUp()).isZero();
        assertThat(financialHistory()).isEqualTo(moneyBefore);
    }

    @Test void twoConcurrentPassesCompleteTheTripOnce() throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        paySeat(trip, local);
        clock.set(ARRIVAL);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var first = pool.submit(() -> { start.await(); return service.terminerTrajetsArrives(); });
            var second = pool.submit(() -> { start.await(); return service.terminerTrajetsArrives(); });
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(0, 1);
        }
        assertThat(status(trip)).isEqualTo("REALISE");
        verify(journal, times(1)).record(SensitiveOperationJournal.Action.TRAJET_REALISE, null,
                SensitiveOperationJournal.Resource.TRAJET, trip);
    }

    @Test void failureRollsBackPendingCancellationsButOtherTripsContinueAndNextPassRetries() throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        long waiting = reserve(trip, vip);
        paySeat(trip, local);
        long otherTrip = createWith(local, home, secondVehicle, TypeTrajet.PUBLIC);
        paySeat(otherTrip, local);
        var moneyBefore = financialHistory();
        clock.set(ARRIVAL);
        doThrow(new IllegalStateException("échec simulé après écriture"))
                .when(journal).record(SensitiveOperationJournal.Action.TRAJET_REALISE, null,
                        SensitiveOperationJournal.Resource.TRAJET, trip);

        assertThat(service.terminerTrajetsArrives()).isEqualTo(1);

        assertThat(status(trip)).isEqualTo("PLANIFIE");
        assertThat(status(otherTrip)).isEqualTo("REALISE");
        assertThat(reservations.findById(waiting).orElseThrow().getStatut()).isEqualTo(StatutReservation.EN_ATTENTE);
        assertThat(financialHistory()).isEqualTo(moneyBefore);
        doCallRealMethod().when(journal).record(SensitiveOperationJournal.Action.TRAJET_REALISE, null,
                SensitiveOperationJournal.Resource.TRAJET, trip);
        assertThat(service.terminerTrajetsArrives()).isEqualTo(1);
        assertThat(status(trip)).isEqualTo("REALISE");
        assertThat(reservations.findById(waiting).orElseThrow().getStatut()).isEqualTo(StatutReservation.ANNULEE);
    }

    @Test void newReservationsAndPaymentsStayForbiddenAtDepartureAndAfterCompletion() throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        long waiting = reserve(trip, vip);
        paySeat(trip, local);
        clock.set(NOW.plusSeconds(172800));
        assertThat(call("POST", "/api/trajets/" + trip + "/reservations", occasional, null).statusCode()).isEqualTo(409);
        assertThat(pay("/api/reservations/" + waiting + "/paiements", vip, UUID.randomUUID(), null).statusCode()).isEqualTo(409);
        clock.set(ARRIVAL);
        assertThat(service.terminerTrajetsArrives()).isEqualTo(1);
        assertThat(call("POST", "/api/trajets/" + trip + "/reservations", occasional, null).statusCode()).isEqualTo(409);
        assertThat(pay("/api/reservations/" + waiting + "/paiements", vip, UUID.randomUUID(), null).statusCode()).isEqualTo(409);
        assertThat(seatPayments.count()).isEqualTo(1);
    }

    @Test void v20ExtendsStatusesWithoutChangingExistingHistory() throws Exception {
        String schema = "completion_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("19").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connection.setSchema(schema);
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("insert into zone(id,nom) values(1,'Zone')");
                statement.execute("insert into vehicule(id,zone_id) values(1,1)");
                statement.execute("insert into client(id,numero,categorie,actif,zone_id) values(1,reserve_client_number('LOCAL'),'LOCAL',true,1)");
                statement.execute("insert into trajet(id,zone_id,vehicule_id,organisateur_id,depart,arrivee,type,origine,destination) " +
                        "values(1,1,1,1,now()+interval '2 days',now()+interval '2 days 1 hour','PUBLIC','A','B')");
                statement.execute("insert into reservation(id,trajet_id,client_id,statut) values(1,1,1,'CONFIRMEE')");
                statement.execute("insert into paiement_place(id,reservation_id,cle_idempotence,montant,mode,valide_le) " +
                        "values(1,1,'" + UUID.randomUUID() + "',20,'SIMULATION',now())");
                statement.execute("insert into dette_trajet(id,trajet_id,client_id,montant) values(1,1,1,80)");
            }
            connection.commit();
            try (var statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.execute("update trajet set statut='REALISE' where id=1"))
                        .isInstanceOf(SQLException.class);
            }
            connection.rollback();
        }
        var upgrade = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("20").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(upgrade.migrate().migrationsExecuted).isZero();
        assertThat(upgrade.validateWithResult().validationSuccessful).isTrue();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connection.setSchema(schema);
            try (var statement = connection.createStatement()) {
                statement.execute("update trajet set statut='REALISE' where id=1");
                try (var rows = statement.executeQuery("select t.statut,r.statut,p.montant,p.rembourse_le,d.montant,d.statut " +
                        "from trajet t join reservation r on r.trajet_id=t.id join paiement_place p on p.reservation_id=r.id " +
                        "join dette_trajet d on d.trajet_id=t.id where t.id=1")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("REALISE");
                    assertThat(rows.getString(2)).isEqualTo("CONFIRMEE");
                    assertThat(rows.getBigDecimal(3)).isEqualByComparingTo("20.00");
                    assertThat(rows.getObject(4)).isNull();
                    assertThat(rows.getBigDecimal(5)).isEqualByComparingTo("80.00");
                    assertThat(rows.getString(6)).isEqualTo("ACTIVE");
                }
                assertThatThrownBy(() -> statement.execute("update trajet set statut='INCONNU' where id=1"))
                        .isInstanceOf(SQLException.class);
            }
        }
    }
}
