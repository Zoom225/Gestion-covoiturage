package com.plateforme.covoiturage;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RebookingMigrationIntegrationTests extends ReportingIntegrationSupport {
    @Autowired PostgreSQLContainer postgres;

    private Flyway migration(String schema, String target) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target(target).load();
    }

    private Connection connection(String schema) throws SQLException {
        var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        connection.setSchema(schema);
        return connection;
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private List<String> history(String schema) throws SQLException {
        var result = new ArrayList<String>();
        try (var connection = connection(schema)) {
            for (String table : List.of("zone", "vehicule", "client", "trajet", "reservation", "paiement_place",
                    "dette_trajet", "paiement_dette")) {
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("select to_jsonb(t)::text from " + table + " t order by id")) {
                    while (rows.next()) result.add(table + ":" + rows.getString(1));
                }
            }
        }
        return result;
    }

    @Test void v15ToV16PreservesHistoricalBookingsPaymentsDebtsAndOtherConstraints() throws Exception {
        String schema = "rebooking_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        migration(schema, "15").migrate();
        try (var connection = connection(schema)) {
            connection.setAutoCommit(false);
            execute(connection, "insert into zone(id,nom) values(1,'Zone historique')");
            execute(connection, "insert into vehicule(id,zone_id) values(1,1)");
            execute(connection, "insert into client(id,numero,categorie,actif,zone_id) values" +
                    "(1,reserve_client_number('LOCAL'),'LOCAL',true,1)," +
                    "(2,reserve_client_number('LOCAL'),'LOCAL',true,1)");
            execute(connection, "insert into trajet(id,zone_id,vehicule_id,organisateur_id,depart,arrivee,type,origine,destination) " +
                    "values(1,1,1,1,now()+interval '2 days',now()+interval '2 days 1 hour','PUBLIC','Départ','Arrivée')");
            execute(connection, "insert into reservation(id,trajet_id,client_id,statut) values" +
                    "(1,1,1,'CONFIRMEE'),(2,1,2,'ANNULEE')");
            execute(connection, "insert into paiement_place(reservation_id,cle_idempotence,montant,mode,valide_le) " +
                    "values(1,'" + UUID.randomUUID() + "',20,'SIMULATION',now())");
            execute(connection, "insert into dette_trajet(id,trajet_id,client_id,montant) values(1,1,1,80)");
            connection.commit();
        }
        var before = history(schema);
        var next = migration(schema, "16");
        assertThat(next.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(next.validateWithResult().validationSuccessful).isTrue();
        assertThat(history(schema)).containsExactlyElementsOf(before);
        try (var connection = connection(schema)) {
            execute(connection, "insert into reservation(id,trajet_id,client_id,statut) values(3,1,2,'EN_ATTENTE')");
            assertThatThrownBy(() -> execute(connection,
                    "insert into reservation(id,trajet_id,client_id,statut) values(4,1,2,'EN_ATTENTE')"))
                    .isInstanceOf(SQLException.class);
            execute(connection, "insert into reservation(id,trajet_id,client_id,statut) values(5,1,2,'ANNULEE')");
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("select indexdef from pg_indexes where schemaname='" + schema +
                         "' and indexname='uq_reservation_client_active'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).contains("statut", "ANNULEE");
            }
        }
    }
}
