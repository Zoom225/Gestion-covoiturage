package com.plateforme.covoiturage;

import com.plateforme.covoiturage.enums.StatutReservation;
import com.plateforme.covoiturage.enums.TypeTrajet;
import com.plateforme.covoiturage.payment.PaymentGateway;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class CancellationIntegrityIntegrationTests extends ReportingIntegrationSupport {
    @MockitoBean PaymentGateway gateway;
    @Autowired PostgreSQLContainer postgres;

    @BeforeEach void paymentSimulation() {
        when(gateway.validate(any(), any())).thenReturn(PaymentGateway.Mode.SIMULATION);
    }

    @Test void paidCancellationKeepsPaymentAndDebtReopensCapacityAndNeedsNewPayment() throws Exception {
        long trip = create(local, TypeTrajet.PUBLIC);
        var fourth = account("O980100", com.plateforme.covoiturage.enums.CategorieClient.OCCASIONNEL, null);
        long oldVip = reserve(trip, vip);
        long occasionalBooking = reserve(trip, occasional);
        long fourthBooking = reserve(trip, fourth);
        paySeat(trip, local);
        assertThat(pay("/api/reservations/" + oldVip + "/paiements", vip, UUID.randomUUID(), null).statusCode()).isEqualTo(200);
        assertThat(pay("/api/reservations/" + occasionalBooking + "/paiements", occasional, UUID.randomUUID(), null).statusCode()).isEqualTo(200);
        assertThat(pay("/api/reservations/" + fourthBooking + "/paiements", fourth, UUID.randomUUID(), null).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select statut from trajet where id=?", String.class, trip)).isEqualTo("COMPLET");
        jdbc.update("insert into dette_trajet(trajet_id,client_id) values(?,?)", trip, local.getClient().getId());

        jdbc.update("update reservation set statut='ANNULEE' where id=?", oldVip);
        assertThat(jdbc.queryForObject("select statut from trajet where id=?", String.class, trip)).isEqualTo("PLANIFIE");
        assertThat(seatPayments.count()).isEqualTo(4);
        assertThat(jdbc.queryForObject("select statut from dette_trajet where trajet_id=?", String.class, trip)).isEqualTo("ACTIVE");
        assertThatThrownBy(() -> jdbc.update("update reservation set statut='CONFIRMEE' where id=?", oldVip))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update reservation set client_id=? where id=?", local.getClient().getId(), oldVip))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        long replacement = reserve(trip, vip);
        assertThat(replacement).isNotEqualTo(oldVip);
        assertThat(pay("/api/reservations/" + replacement + "/paiements", vip, UUID.randomUUID(), null).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select statut from trajet where id=?", String.class, trip)).isEqualTo("COMPLET");
        assertThat(reservations.countByTrajetIdAndStatut(trip, StatutReservation.CONFIRMEE)).isEqualTo(4);
        assertThat(reservations.findById(oldVip).orElseThrow().getStatut()).isEqualTo(StatutReservation.ANNULEE);
        assertThat(seatPayments.count()).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from paiement_place where reservation_id=?", Long.class, oldVip)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select statut from dette_trajet where trajet_id=?", String.class, trip)).isEqualTo("ACTIVE");
    }

    @Test void cancelledParticipantCannotReadPrivateTripButOrganizerCan() throws Exception {
        long trip = create(local, TypeTrajet.PRIVE);
        var added = call("POST", "/api/trajets/" + trip + "/participants", local,
                java.util.Map.of("clientId", vip.getClient().getId()));
        assertThat(added.statusCode()).isEqualTo(201);
        assertThat(call("GET", "/api/trajets/" + trip, vip, null).statusCode()).isEqualTo(200);
        jdbc.update("update reservation set statut='ANNULEE' where id=?", read(added).path("reservationId").asLong());
        assertThat(call("GET", "/api/trajets/" + trip, vip, null).statusCode()).isEqualTo(404);
        assertThat(read(call("GET", "/api/trajets", vip, null)).path("totalElements").asLong()).isZero();
        assertThat(call("GET", "/api/trajets/" + trip, local, null).statusCode()).isEqualTo(200);
    }

    @Test void v16UpgradeRetainsPaidHistoryAndDebt() throws Exception {
        String schema = "cancel_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("16").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connection.setSchema(schema);
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("insert into zone(id,nom) values(1,'Zone')");
                statement.execute("insert into vehicule(id,zone_id) values(1,1)");
                statement.execute("insert into client(id,numero,categorie,actif,zone_id) values(1,reserve_client_number('LOCAL'),'LOCAL',true,1)");
                statement.execute("insert into trajet(id,zone_id,vehicule_id,organisateur_id,depart,arrivee,type,origine,destination) " +
                        "values(1,1,1,1,now()+interval '2 days',now()+interval '2 days 1 hour','PUBLIC','Départ','Arrivée')");
                statement.execute("insert into reservation(id,trajet_id,client_id,statut) values(1,1,1,'CONFIRMEE')");
                statement.execute("insert into paiement_place(reservation_id,cle_idempotence,montant,mode,valide_le) " +
                        "values(1,'" + UUID.randomUUID() + "',20,'SIMULATION',now())");
                statement.execute("insert into dette_trajet(id,trajet_id,client_id,montant) values(1,1,1,80)");
            }
            connection.commit();
        }
        var upgrade = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("17").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(upgrade.validateWithResult().validationSuccessful).isTrue();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connection.setSchema(schema);
            try (var statement = connection.createStatement()) {
                statement.execute("update reservation set statut='ANNULEE' where id=1");
                try (var rows = statement.executeQuery("select r.statut,p.montant,d.statut from reservation r " +
                        "join paiement_place p on p.reservation_id=r.id join dette_trajet d on d.trajet_id=r.trajet_id where r.id=1")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("ANNULEE");
                    assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("20.00");
                    assertThat(rows.getString(3)).isEqualTo("ACTIVE");
                }
                statement.execute("insert into reservation(id,trajet_id,client_id,statut) values(2,1,1,'EN_ATTENTE')");
                assertThatThrownBy(() -> statement.execute("update reservation set statut='CONFIRMEE' where id=2"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> statement.execute("insert into paiement_place(reservation_id,cle_idempotence,montant,mode,valide_le) " +
                        "values(2,'" + UUID.randomUUID() + "',20,'SIMULATION',now())"))
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    @Test void v19AddsRefundTrackingWithoutChangingExistingPaymentOrDebt() throws Exception {
        String schema = "refund_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("18").load().migrate();
        UUID paymentKey = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connection.setSchema(schema);
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("insert into zone(id,nom) values(1,'Zone')");
                statement.execute("insert into vehicule(id,zone_id) values(1,1)");
                statement.execute("insert into client(id,numero,categorie,actif,zone_id) values(1,reserve_client_number('LOCAL'),'LOCAL',true,1)");
                statement.execute("insert into trajet(id,zone_id,vehicule_id,organisateur_id,depart,arrivee,type,origine,destination) " +
                        "values(1,1,1,1,now()+interval '2 days',now()+interval '2 days 1 hour','PUBLIC','Départ','Arrivée')");
                statement.execute("insert into reservation(id,trajet_id,client_id,statut) values(1,1,1,'CONFIRMEE')");
                statement.execute("insert into paiement_place(id,reservation_id,cle_idempotence,montant,mode,valide_le) " +
                        "values(1,1,'" + paymentKey + "',20,'SIMULATION',now())");
                statement.execute("insert into dette_trajet(id,trajet_id,client_id,montant) values(1,1,1,80)");
            }
            connection.commit();
        }

        var upgrade = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("19").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(upgrade.migrate().migrationsExecuted).isZero();
        assertThat(upgrade.validateWithResult().validationSuccessful).isTrue();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connection.setSchema(schema);
            try (var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("select r.statut,p.cle_idempotence,p.montant,p.rembourse_le,d.montant,d.statut " +
                        "from reservation r join paiement_place p on p.reservation_id=r.id " +
                        "join dette_trajet d on d.trajet_id=r.trajet_id where r.id=1")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("CONFIRMEE");
                    assertThat(rows.getObject(2, UUID.class)).isEqualTo(paymentKey);
                    assertThat(rows.getBigDecimal(3)).isEqualByComparingTo("20.00");
                    assertThat(rows.getObject(4)).isNull();
                    assertThat(rows.getBigDecimal(5)).isEqualByComparingTo("80.00");
                    assertThat(rows.getString(6)).isEqualTo("ACTIVE");
                }
                assertThatThrownBy(() -> statement.execute("update paiement_place set rembourse_le=valide_le-interval '1 second' where id=1"))
                        .isInstanceOf(SQLException.class);
                statement.execute("update reservation set statut='ANNULEE' where id=1");
                statement.execute("update paiement_place set rembourse_le=valide_le+interval '1 hour' where id=1");
            }
        }
    }
}
