package com.plateforme.covoiturage;

import com.plateforme.covoiturage.enums.StatutReservation;
import com.plateforme.covoiturage.enums.TypeTrajet;
import com.plateforme.covoiturage.payment.PaymentGateway;
import com.plateforme.covoiturage.exception.PaymentUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReservationCancellationIntegrationTests extends ReportingIntegrationSupport {
    @MockitoBean PaymentGateway gateway;

    @BeforeEach void paymentSimulation() {
        when(gateway.validate(any(), any())).thenReturn(PaymentGateway.Mode.SIMULATION);
    }

    private String cancel(long id) { return "/api/reservations/" + id + "/annulation"; }

    @ParameterizedTest
    @CsvSource({"25,20.00", "24,20.00", "23,0.00"})
    void confirmedCancellationRefundsOnlyAtLeast24HoursBeforeDeparture(int hoursBefore, String expectedAmount) throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        long booking = reserve(trip, vip);
        paySeat(trip, vip);
        var paymentBefore = seatPayments.findByReservationId(booking).orElseThrow();
        Instant cancelledAt = NOW.plusSeconds(172800).minus(Duration.ofHours(hoursBefore));
        clock.set(cancelledAt);

        assertThat(call("POST", cancel(booking), vip, null).statusCode()).isEqualTo(200);

        assertThat(reservations.findById(booking).orElseThrow().getStatut()).isEqualTo(StatutReservation.ANNULEE);
        var paymentAfter = seatPayments.findByReservationId(booking).orElseThrow();
        assertThat(paymentAfter.getId()).isEqualTo(paymentBefore.getId());
        assertThat(paymentAfter.getMontant()).isEqualByComparingTo("20.00");
        assertThat(paymentAfter.getValideLe()).isEqualTo(paymentBefore.getValideLe());
        var history = read(call("GET", "/api/paiements/me", vip, null)).path("content").get(0);
        assertThat(history.path("montant").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(history.path("montantRembourse").decimalValue()).isEqualByComparingTo(expectedAmount);
        if (hoursBefore >= 24) {
            assertThat(paymentAfter.getRembourseLe()).isEqualTo(cancelledAt);
            assertThat(history.path("rembourseLe").asText()).isEqualTo(cancelledAt.toString());
            verify(gateway).refund(paymentBefore.getCleIdempotence(), new BigDecimal("20.00"));
        } else {
            assertThat(paymentAfter.getRembourseLe()).isNull();
            verify(gateway, never()).refund(any(), any());
        }
    }

    @Test void pendingCancellationDoesNotCreatePaymentOrRefund() throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        long booking = reserve(trip, vip);

        assertThat(call("POST", cancel(booking), vip, null).statusCode()).isEqualTo(200);

        assertThat(reservations.findById(booking).orElseThrow().getStatut()).isEqualTo(StatutReservation.ANNULEE);
        assertThat(seatPayments.count()).isZero();
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void confirmedCancellationAtOrAfterDepartureIsRefused(long secondsAfterDeparture) throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        long booking = reserve(trip, vip);
        paySeat(trip, vip);
        clock.set(NOW.plusSeconds(172800 + secondsAfterDeparture));

        var response = call("POST", cancel(booking), vip, null);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(read(response).path("code").asText()).isEqualTo("DEPARTURE_PASSED");
        assertThat(reservations.findById(booking).orElseThrow().getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
        assertThat(seatPayments.findByReservationId(booking).orElseThrow().getRembourseLe()).isNull();
        verify(gateway, never()).refund(any(), any());
    }

    @Test void repeatedCancellationKeepsOneRefundAndItsOriginalDate() throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        long booking = reserve(trip, vip);
        paySeat(trip, vip);

        assertThat(call("POST", cancel(booking), vip, null).statusCode()).isEqualTo(200);
        clock.set(NOW.plusSeconds(3600));
        assertThat(call("POST", cancel(booking), vip, null).statusCode()).isEqualTo(200);

        assertThat(seatPayments.count()).isEqualTo(1);
        assertThat(seatPayments.findByReservationId(booking).orElseThrow().getRembourseLe()).isEqualTo(NOW);
        verify(gateway, times(1)).refund(any(), any());
    }

    @Test void concurrentCancellationsRefundOnce() throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        long booking = reserve(trip, vip);
        paySeat(trip, vip);

        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var first = pool.submit(() -> { start.await(); return call("POST", cancel(booking), vip, null).statusCode(); });
            var second = pool.submit(() -> { start.await(); return call("POST", cancel(booking), vip, null).statusCode(); });
            start.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        }

        assertThat(reservations.findById(booking).orElseThrow().getStatut()).isEqualTo(StatutReservation.ANNULEE);
        assertThat(seatPayments.findByReservationId(booking).orElseThrow().getRembourseLe()).isEqualTo(NOW);
        verify(gateway, times(1)).refund(any(), any());
    }

    @Test void refundFailureRollsBackCancellationAndCapacityThenAllowsRetry() throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        var fourth = account("O980100", com.plateforme.covoiturage.enums.CategorieClient.OCCASIONNEL, null);
        long booking = reserve(trip, vip);
        reserve(trip, occasional);
        reserve(trip, fourth);
        paySeat(trip, local);
        paySeat(trip, vip);
        paySeat(trip, occasional);
        paySeat(trip, fourth);
        doThrow(new PaymentUnavailableException()).when(gateway).refund(any(), any());

        assertThat(call("POST", cancel(booking), vip, null).statusCode()).isEqualTo(503);

        assertThat(reservations.findById(booking).orElseThrow().getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
        assertThat(jdbc.queryForObject("select statut from trajet where id=?", String.class, trip)).isEqualTo("COMPLET");
        assertThat(seatPayments.findByReservationId(booking).orElseThrow().getRembourseLe()).isNull();
        assertThat(seatPayments.count()).isEqualTo(4);

        doNothing().when(gateway).refund(any(), any());
        assertThat(call("POST", cancel(booking), vip, null).statusCode()).isEqualTo(200);
        assertThat(reservations.findById(booking).orElseThrow().getStatut()).isEqualTo(StatutReservation.ANNULEE);
        assertThat(jdbc.queryForObject("select statut from trajet where id=?", String.class, trip)).isEqualTo("PLANIFIE");
        assertThat(seatPayments.findByReservationId(booking).orElseThrow().getRembourseLe()).isEqualTo(NOW);
    }

    @Test void cancellingEntireTripRemainsForbidden() throws Exception {
        long trip = create(local, TypeTrajet.PRIVE);

        assertThat(call("POST", "/api/trajets/" + trip + "/annulation", local, null).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("select statut from trajet where id=?", String.class, trip)).isEqualTo("PLANIFIE");
        verify(gateway, never()).refund(any(), any());
    }

    @Test void ownerCancelsPaidBookingRebooksAndPaysAgainWithoutLosingHistoryOrDebt() throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        long oldBooking = reserve(trip, vip);
        assertThat(pay("/api/reservations/" + oldBooking + "/paiements", vip, UUID.randomUUID(), null).statusCode()).isEqualTo(200);
        jdbc.update("insert into dette_trajet(trajet_id,client_id) values(?,?)", trip, local.getClient().getId());
        long clientsBefore = clients.count();

        var cancelled = call("POST", cancel(oldBooking), vip, null);
        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(read(cancelled).path("statut").asText()).isEqualTo("ANNULEE");
        assertThat(call("POST", cancel(oldBooking), vip, null).statusCode()).isEqualTo(200);
        assertThat(seatPayments.count()).isEqualTo(1);
        assertThat(seatPayments.findByReservationId(oldBooking).orElseThrow().getRembourseLe()).isEqualTo(NOW);
        verify(gateway, times(1)).refund(any(), any());
        assertThat(jdbc.queryForObject("select statut from dette_trajet where trajet_id=?", String.class, trip)).isEqualTo("ACTIVE");

        long replacement = reserve(trip, vip);
        assertThat(replacement).isNotEqualTo(oldBooking);
        assertThat(pay("/api/reservations/" + replacement + "/paiements", vip, UUID.randomUUID(), null).statusCode()).isEqualTo(200);
        assertThat(seatPayments.count()).isEqualTo(2);
        assertThat(seatPayments.findByReservationId(replacement).orElseThrow().getRembourseLe()).isNull();
        assertThat(read(call("GET", "/api/paiements/me", vip, null)).path("totalElements").asLong()).isEqualTo(2);
        assertThat(reservations.findById(oldBooking).orElseThrow().getStatut()).isEqualTo(StatutReservation.ANNULEE);
        assertThat(reservations.findById(replacement).orElseThrow().getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
        assertThat(clients.count()).isEqualTo(clientsBefore);

        var anotherTrip = new HashMap<String,Object>(request(NOW.plusSeconds(259200),NOW.plusSeconds(262800),TypeTrajet.PUBLIC));
        anotherTrip.put("vehiculeId", secondVehicle.getId());
        assertThat(call("POST", "/api/trajets", local, anotherTrip).statusCode()).isEqualTo(409);
        long debt = jdbc.queryForObject("select id from dette_trajet where trajet_id=?", Long.class, trip);
        assertThat(pay("/api/dettes/" + debt + "/paiements", local, UUID.randomUUID(), Map.of("montant", "20.00")).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select statut from dette_trajet where id=?", String.class, debt)).isEqualTo("ACTIVE");
        assertThat(call("POST", "/api/trajets", local, anotherTrip).statusCode()).isEqualTo(409);
        assertThat(pay("/api/dettes/" + debt + "/paiements", local, UUID.randomUUID(), Map.of("montant", "60.00")).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select statut from dette_trajet where id=?", String.class, debt)).isEqualTo("REGULARISEE");
        assertThat(call("POST", "/api/trajets", local, anotherTrip).statusCode()).isEqualTo(201);
    }

    @Test void ownershipJwtAndDepartureBoundaryAreEnforced() throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        long booking = reserve(trip, vip);
        assertThat(call("POST", cancel(booking), null, null).statusCode()).isEqualTo(401);
        assertThat(call("POST", cancel(booking), global, null).statusCode()).isEqualTo(403);
        assertThat(call("POST", cancel(booking), adminLocal(), null).statusCode()).isEqualTo(403);
        assertThat(call("POST", cancel(booking), local, null).statusCode()).isEqualTo(404);
        assertThat(call("POST", cancel(Long.MAX_VALUE), vip, null).statusCode()).isEqualTo(404);
        clock.set(NOW.plusSeconds(172800));
        assertThat(call("POST", cancel(booking), vip, null).statusCode()).isEqualTo(409);
        assertThat(reservations.findById(booking).orElseThrow().getStatut()).isEqualTo(StatutReservation.EN_ATTENTE);
    }

    @Test void activeDebtDoesNotPreventCancellationButBlocksRebookingUntilFullySettled() throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        long booking = reserve(trip, vip);
        var debtTripRequest = new HashMap<String,Object>(request(NOW.plusSeconds(259200),NOW.plusSeconds(262800),TypeTrajet.PUBLIC));
        debtTripRequest.put("vehiculeId", secondVehicle.getId());
        long debtTrip = read(call("POST", "/api/trajets", vip, debtTripRequest)).path("trajetId").asLong();
        jdbc.update("insert into dette_trajet(trajet_id,client_id) values(?,?)", debtTrip, vip.getClient().getId());
        long debt = jdbc.queryForObject("select id from dette_trajet where trajet_id=?", Long.class, debtTrip);

        assertThat(call("POST", cancel(booking), vip, null).statusCode()).isEqualTo(200);
        assertThat(call("POST", "/api/trajets/" + trip + "/reservations", vip, null).statusCode()).isEqualTo(409);
        assertThat(pay("/api/dettes/" + debt + "/paiements", vip, UUID.randomUUID(), Map.of("montant", "20.00")).statusCode()).isEqualTo(200);
        assertThat(call("POST", "/api/trajets/" + trip + "/reservations", vip, null).statusCode()).isEqualTo(409);
        assertThat(pay("/api/dettes/" + debt + "/paiements", vip, UUID.randomUUID(), Map.of("montant", "60.00")).statusCode()).isEqualTo(200);
        assertThat(reserve(trip, vip)).isNotEqualTo(booking);
        assertThat(jdbc.queryForObject("select statut from dette_trajet where id=?", String.class, debt)).isEqualTo("REGULARISEE");
    }

    @Test void cancellingOwnPrivateBookingBeforeJ1DoesNotAvoidOrganizerDebt() throws Exception {
        long trip = create(local, TypeTrajet.PRIVE);
        long ownBooking = booking(trip, local);
        long clientsBefore = clients.count();
        assertThat(call("POST", cancel(ownBooking), local, null).statusCode()).isEqualTo(200);
        clock.set(deadline());
        assertThat(j1.catchUp()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select type from trajet where id=?", String.class, trip)).isEqualTo("PUBLIC");
        assertThat(jdbc.queryForObject("select statut from dette_trajet where trajet_id=?", String.class, trip)).isEqualTo("ACTIVE");
        assertThat(clients.count()).isEqualTo(clientsBefore);
    }

    @Test void paymentAndCancellationSerializeOnTheSameTrip() throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        long booking = reserve(trip, vip);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var cancellation = pool.submit(() -> { start.await(); return call("POST", cancel(booking), vip, null).statusCode(); });
            var payment = pool.submit(() -> { start.await(); return pay("/api/reservations/" + booking + "/paiements", vip, UUID.randomUUID(), null).statusCode(); });
            start.countDown();
            assertThat(cancellation.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(payment.get(30, TimeUnit.SECONDS)).isIn(200, 409);
        }
        assertThat(reservations.findById(booking).orElseThrow().getStatut()).isEqualTo(StatutReservation.ANNULEE);
        assertThat(seatPayments.count()).isBetween(0L, 1L);
        assertThat(reserve(trip, vip)).isNotEqualTo(booking);
        assertThat(reservations.countByTrajetIdAndStatut(trip, StatutReservation.CONFIRMEE)).isZero();
    }
}
