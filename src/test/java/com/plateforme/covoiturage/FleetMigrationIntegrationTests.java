package com.plateforme.covoiturage;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class FleetMigrationIntegrationTests extends ReportingIntegrationSupport {
    @Autowired PostgreSQLContainer postgres;
    Connection connection(String schema)throws SQLException{
        var c=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
        c.setSchema(schema);return c;
    }
    Flyway migration(String schema,String target){
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target(target).load();
    }
    void sql(Connection c,String command)throws SQLException{
        try(var statement=c.createStatement()){statement.execute(command);}
    }
    List<String> snapshot(String schema)throws SQLException{
        var rows=new ArrayList<String>();
        try(var c=connection(schema)){
            for(String table:List.of("zone","vehicule","client","trajet","reservation","paiement_place","dette_trajet","paiement_dette","indisponibilite","flyway_schema_history")){
                String expression=table.equals("vehicule")?"(to_jsonb(t)-'actif')::text":"to_jsonb(t)::text";
                String where=table.equals("flyway_schema_history")?" where version is null or version::int<=14":"";
                try(var s=c.createStatement();var r=s.executeQuery("select "+expression+" from "+table+" t"+where+" order by 1")){
                    while(r.next())rows.add(table+":"+r.getString(1));
                }
            }
        }
        return rows;
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void additiveUpgradePreservesHistoryOrRefusesConflictsWithoutReconciliation(boolean conflict)throws Exception{
        String schema="fleet_upgrade_"+UUID.randomUUID().toString().replace("-","");
        migration(schema,"14").migrate();
        try(var c=connection(schema)){
            c.setAutoCommit(false);
            sql(c,"insert into zone(id,nom) values(1,'Zone historique')");
            sql(c,"insert into vehicule(id,zone_id) values(1,1)");
            sql(c,"insert into client(id,numero,categorie,actif,zone_id) values(1,reserve_client_number('LOCAL'),'LOCAL',true,1)");
            sql(c,"insert into trajet(id,zone_id,vehicule_id,organisateur_id,depart,arrivee,type,origine,destination) values(1,1,1,1,now()+interval '2 days',now()+interval '2 days 1 hour','PUBLIC','Départ historique','Arrivée historique')");
            sql(c,"insert into reservation(id,trajet_id,client_id,statut) values(1,1,1,'CONFIRMEE')");
            sql(c,"insert into paiement_place(reservation_id,cle_idempotence,montant,mode,valide_le) values(1,'"+UUID.randomUUID()+"',20,'SIMULATION',now())");
            sql(c,"insert into dette_trajet(id,trajet_id,client_id,montant) values(1,1,1,80)");
            if(conflict)sql(c,"insert into indisponibilite(zone_id,debut,fin) select 1,depart,arrivee from trajet where id=1");
            c.commit();
        }
        var before=snapshot(schema);
        var next=migration(schema,"15");
        if(conflict){
            assertThatThrownBy(next::migrate).isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("Existing availability conflict requires approved reconciliation");
            assertThat(migration(schema,"14").info().current().getVersion().getVersion()).isEqualTo("14");
            try(var c=connection(schema);var s=c.prepareStatement("select count(*) from information_schema.columns where table_schema=? and table_name='vehicule' and column_name='actif'")){
                s.setString(1,schema);try(var r=s.executeQuery()){r.next();assertThat(r.getInt(1)).isZero();}
            }
        }else{
            assertThat(next.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(next.validateWithResult().validationSuccessful).isTrue();
            try(var c=connection(schema);var s=c.createStatement();var r=s.executeQuery("select actif from vehicule where id=1")){
                r.next();assertThat(r.getBoolean(1)).isTrue();
            }
        }
        assertThat(snapshot(schema)).containsExactlyElementsOf(before);
    }
}
