package com.plateforme.covoiturage;

import com.plateforme.covoiturage.enums.StatutReservation;
import com.plateforme.covoiturage.enums.TypeTrajet;
import com.plateforme.covoiturage.payment.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class RebookingIntegrationTests extends ReportingIntegrationSupport {
    @MockitoBean PaymentGateway gateway;

    @BeforeEach void paymentSimulation() {
        when(gateway.validate(any(), any())).thenReturn(PaymentGateway.Mode.SIMULATION);
    }

    @Test void cancelledHistoryRemainsAndNewBookingRequiresItsOwnTwentyEuroPayment() throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        long previous = reserve(trip, vip);
        jdbc.update("update reservation set statut='ANNULEE' where id=?", previous);

        long current = reserve(trip, vip);
        assertThat(current).isNotEqualTo(previous);
        assertThat(call("POST", "/api/trajets/" + trip + "/reservations", vip, null).statusCode()).isEqualTo(409);
        assertThat(pay("/api/reservations/" + previous + "/paiements", vip, UUID.randomUUID(), null).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from reservation where trajet_id=? and client_id=?", Long.class,
                trip, vip.getClient().getId())).isEqualTo(2);
        assertThat(seatPayments.count()).isZero();
        var payment = pay("/api/reservations/" + current + "/paiements", vip, UUID.randomUUID(), null);
        assertThat(payment.statusCode()).isEqualTo(200);
        assertThat(read(payment).path("montant").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(reservations.findById(previous).orElseThrow().getStatut()).isEqualTo(StatutReservation.ANNULEE);
        assertThat(reservations.findById(current).orElseThrow().getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
        assertThat(seatPayments.count()).isEqualTo(1);
    }

    @Test void concurrentRebookingCreatesOnlyOneActiveReservation() throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        long previous = reserve(trip, vip);
        jdbc.update("update reservation set statut='ANNULEE' where id=?", previous);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var task = (java.util.concurrent.Callable<Integer>) () -> {
                start.await();
                return call("POST", "/api/trajets/" + trip + "/reservations", vip, null).statusCode();
            };
            var first = pool.submit(task);
            var second = pool.submit(task);
            start.countDown();
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from reservation where trajet_id=? and client_id=? and statut<>'ANNULEE'",
                Long.class, trip, vip.getClient().getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from reservation where trajet_id=? and client_id=?",
                Long.class, trip, vip.getClient().getId())).isEqualTo(2);
        assertThat(reservations.countByTrajetIdAndStatut(trip, StatutReservation.CONFIRMEE)).isZero();
    }
}
