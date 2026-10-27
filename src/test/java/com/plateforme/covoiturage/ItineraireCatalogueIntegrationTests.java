package com.plateforme.covoiturage;

import com.plateforme.covoiturage.entity.Compte;
import com.plateforme.covoiturage.entity.Vehicule;
import com.plateforme.covoiturage.enums.CategorieClient;
import com.plateforme.covoiturage.enums.StatutReservation;
import com.plateforme.covoiturage.enums.TypeTrajet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class ItineraireCatalogueIntegrationTests extends ReportingIntegrationSupport {
    private static final String API="/api/itineraires";
    private static final List<String> ROUTES=List.of("Bruxelles → Mons","Mons → Bruxelles",
            "Charleroi → Nivelles","Nivelles → Charleroi","Zaventem → Mons","Bruxelles → Namur",
            "Bruxelles → Charleroi","Bruxelles → Liège","Namur → Charleroi","Zaventem → Bruxelles");
    private static final List<String> BUSINESS_TABLES=List.of("zone","vehicule","client","compte","trajet",
            "reservation","paiement_place","dette_trajet","paiement_dette");
    @Autowired PostgreSQLContainer postgres;

    private String encode(String value) { return URLEncoder.encode(value==null?"":value,StandardCharsets.UTF_8); }

    private Map<String,Long> businessCounts() {
        var result=new LinkedHashMap<String,Long>();
        for(String table:BUSINESS_TABLES) result.put(table,jdbc.queryForObject("select count(*) from "+table,Long.class));
        return result;
    }

    @Test void catalogueContainsOnlyTheTenReferencePairsAndReadingCreatesNothing()throws Exception {
        var before=businessCounts();
        var response=call("GET",API,local,null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        var page=read(response);
        assertThat(page.path("totalElements").asLong()).isEqualTo(10);
        var pairs=new ArrayList<String>();
        var ids=new HashSet<Long>();
        for(var route:page.path("content")) {
            pairs.add(route.path("origine").asText()+" → "+route.path("destination").asText());
            assertThat(ids.add(route.path("itineraireId").asLong())).isTrue();
            for(String forbidden:List.of("trajetId","vehiculeId","zoneId","organisateurId","depart","reservationId"))
                assertThat(route.has(forbidden)).isFalse();
        }
        assertThat(pairs).containsExactlyElementsOf(ROUTES);
        assertThat(businessCounts()).isEqualTo(before);
        assertThat(trips.count()).isZero();
        assertThat(reservations.count()).isZero();
        assertThat(seatPayments.count()).isZero();
    }

    @ParameterizedTest @EnumSource(CategorieClient.class)
    void activeClientsCanReadReferencesWithoutAcquiringZoneRights(CategorieClient category)throws Exception {
        Compte actor=switch(category){case LOCAL->otherLocal;case VIP->ungrantedVip;case OCCASIONNEL->occasional;};
        assertThat(read(call("GET",API,actor,null)).path("totalElements").asLong()).isEqualTo(10);
        if(category==CategorieClient.VIP) {
            assertThat(read(call("GET","/api/zones",actor,null)).path("totalElements").asLong()).isZero();
            assertThat(call("POST","/api/trajets",actor,request(NOW.plusSeconds(172800),NOW.plusSeconds(176400),TypeTrajet.PUBLIC))
                    .statusCode()).isEqualTo(403);
        }
    }

    @ParameterizedTest @CsvSource({"anonymous,401","invalidJwt,401","global,403","localAdmin,403","inactiveAccount,401","inactiveClient,401"})
    void catalogueRequiresAnEffectiveClientJwt(String kind,int expected)throws Exception {
        var before=businessCounts();
        if(kind.equals("invalidJwt")) {
            var response=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+API))
                    .header("Authorization","Bearer invalide").GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(expected);
        } else {
            Compte actor=switch(kind){case "anonymous"->null;case "global"->global;case "localAdmin"->adminLocal();default->local;};
            if(kind.equals("inactiveAccount")) jdbc.update("update compte set actif=false where id=?",local.getId());
            if(kind.equals("inactiveClient")) jdbc.update("update client set actif=false where id=?",local.getClient().getId());
            before=businessCounts();
            assertThat(call("GET",API,actor,null).statusCode()).isEqualTo(expected);
        }
        assertThat(businessCounts()).isEqualTo(before);
    }

    @ParameterizedTest @CsvSource(value={"brux;mon;1","BRUX;;4",";brux;2","zaven;;2",";char;3","Bruxelles;Liège;1","inconnu;;0"},delimiter=';')
    void searchesBothFragmentsWithoutCaseBeforePagination(String origin,String destination,long count)throws Exception {
        var response=call("GET",API+"?origine="+encode(origin)+"&destination="+encode(destination)+"&size=1",local,null);
        assertThat(response.statusCode()).isEqualTo(200);
        var page=read(response);
        assertThat(page.path("totalElements").asLong()).isEqualTo(count);
        assertThat(page.path("content").size()).isEqualTo((int)Math.min(count,1));
        assertThat(page.path("totalPages").asLong()).isEqualTo(count);
    }

    @ParameterizedTest @ValueSource(strings={"%","_","' OR 1=1 --"})
    void searchTreatsWildcardsAndSqlAsLiteralInput(String input)throws Exception {
        var response=call("GET",API+"?origine="+encode(input),local,null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(read(response).path("totalElements").asLong()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from itineraire_propose",Long.class)).isEqualTo(10);
    }

    @Test void paginationIsStableCompleteAndHasNoDuplicateBetweenPages()throws Exception {
        var ids=new LinkedHashSet<Long>();
        for(int index=0;index<4;index++) {
            var page=read(call("GET",API+"?size=3&page="+index,local,null));
            assertThat(page.path("totalElements").asLong()).isEqualTo(10);
            assertThat(page.path("totalPages").asInt()).isEqualTo(4);
            assertThat(page.path("content").size()).isEqualTo(index==3?1:3);
            assertThat(read(call("GET",API+"?size=3&page="+index,local,null)).path("content")).isEqualTo(page.path("content"));
            for(var route:page.path("content")) assertThat(ids.add(route.path("itineraireId").asLong())).isTrue();
        }
        assertThat(new ArrayList<>(ids)).hasSize(10).isSorted();
        var empty=read(call("GET",API+"?size=3&page=4",local,null));
        assertThat(empty.path("content").size()).isZero();
        assertThat(empty.path("totalElements").asLong()).isEqualTo(10);
    }

    @ParameterizedTest @ValueSource(strings={"page=-1","size=0","size=101","page=2147483647&size=100"})
    void invalidPaginationKeepsTheExistingValidationContract(String query)throws Exception {
        var response=call("GET",API+"?"+query,local,null);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(read(response).path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @ParameterizedTest @ValueSource(strings={"origine","destination"})
    void invalidFilterLengthIsRejectedInFrench(String field)throws Exception {
        var response=call("GET",API+"?"+field+"="+"a".repeat(256),local,null);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(read(response).path("validationErrors").get(0).path("message").asText()).contains("255","caractères");
    }

    @ParameterizedTest @ValueSource(strings={"catalogue","personnalise"})
    void referenceAndCustomPlacesUseTheSameExplicitCreationApi(String mode)throws Exception {
        String origin="Départ personnalisé",destination="Arrivée personnalisée";
        if(mode.equals("catalogue")) {
            var reference=read(call("GET",API+"?origine=Bruxelles&destination=Mons",local,null)).path("content").get(0);
            origin=reference.path("origine").asText();destination=reference.path("destination").asText();
        }
        assertThat(trips.count()).isZero();assertThat(reservations.count()).isZero();
        var body=new HashMap<String,Object>(request(NOW.plusSeconds(172800),NOW.plusSeconds(176400),TypeTrajet.PUBLIC));
        body.put("origine",origin);body.put("destination",destination);
        var response=call("POST","/api/trajets",local,body);
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(read(response).path("origine").asText()).isEqualTo(origin);
        assertThat(read(response).path("destination").asText()).isEqualTo(destination);
        assertThat(read(response).path("organisateurId").asLong()).isEqualTo(local.getClient().getId());
        assertThat(trips.count()).isEqualTo(1);assertThat(reservations.count()).isEqualTo(1);
        assertThat(reservations.countByTrajetIdAndStatut(read(response).path("trajetId").asLong(),StatutReservation.CONFIRMEE)).isZero();
        assertThat(seatPayments.count()).isZero();assertThat(debts.count()).isZero();
    }

    @ParameterizedTest @CsvSource({"forbiddenZone,403","wrongVehicleZone,409","unavailableVehicle,409","inactiveVehicle,409","activeDebt,409","missingDates,400","horizon,409"})
    void choosingAReferenceNeverBypassesRealTripRules(String scenario,int expected)throws Exception {
        var reference=read(call("GET",API+"?origine=Bruxelles&destination=Mons",local,null)).path("content").get(0);
        var body=new HashMap<String,Object>(request(NOW.plusSeconds(172800),NOW.plusSeconds(176400),TypeTrajet.PUBLIC));
        body.put("origine",reference.path("origine").asText());body.put("destination",reference.path("destination").asText());
        switch(scenario) {
            case "forbiddenZone" -> { body.put("zoneId",away.getId());body.put("vehiculeId",vehicles.saveAndFlush(new Vehicule(away)).getId()); }
            case "wrongVehicleZone" -> body.put("vehiculeId",vehicles.saveAndFlush(new Vehicule(away)).getId());
            case "unavailableVehicle" -> jdbc.update("insert into indisponibilite(vehicule_id,debut,fin) values(?,?,?)",
                    vehicle.getId(),Timestamp.from(NOW.plusSeconds(172800)),Timestamp.from(NOW.plusSeconds(176400)));
            case "inactiveVehicle" -> jdbc.update("update vehicule set actif=false where id=?",vehicle.getId());
            case "activeDebt" -> { long existing=createWith(local,home,secondVehicle,TypeTrajet.PRIVE);
                jdbc.update("insert into dette_trajet(trajet_id,client_id) values(?,?)",existing,local.getClient().getId()); }
            case "missingDates" -> body.remove("depart");
            case "horizon" -> { body.put("depart",NOW.plusSeconds(11*86400).toString());body.put("arrivee",NOW.plusSeconds(11*86400+3600).toString()); }
            default -> throw new IllegalArgumentException(scenario);
        }
        var before=businessCounts();
        assertThat(call("POST","/api/trajets",local,body).statusCode()).isEqualTo(expected);
        assertThat(businessCounts()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings={"POST","PUT","PATCH","DELETE"})
    void catalogueHasNoClientWriteApi(String method)throws Exception {
        assertThat(call(method,API,local,Map.of("origine","A","destination","B")).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from itineraire_propose",Long.class)).isEqualTo(10);
    }

    @ParameterizedTest @ValueSource(strings={"Bruxelles","bRuXeLlEs"})
    void postgresRejectsDuplicateDirectedPairsWithoutCase(String origin) {
        assertThatThrownBy(() -> jdbc.update("insert into itineraire_propose(origine,destination) values(?,?)",origin,"Mons"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from itineraire_propose",Long.class)).isEqualTo(10);
    }

    @ParameterizedTest @ValueSource(strings={""," ","\t"})
    void postgresRejectsEmptyReferencePlaces(String empty) {
        assertThatThrownBy(() -> jdbc.update("insert into itineraire_propose(origine,destination) values(?,?)",empty,"X"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into itineraire_propose(origine,destination) values(?,?)","X",empty))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Flyway migration(String schema,String target) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target(target).load();
    }

    private Connection connection(String schema)throws Exception {
        var connection=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
        connection.setSchema(schema);return connection;
    }

    private List<String> history(String schema)throws Exception {
        var result=new ArrayList<String>();
        try(var connection=connection(schema);var statement=connection.createStatement()) {
            for(String table:BUSINESS_TABLES) try(var rows=statement.executeQuery("select to_jsonb(t)::text from "+table+" t order by id")) {
                while(rows.next()) result.add(table+":"+rows.getString(1));
            }
        }
        return result;
    }

    @Test void v17UpgradePreservesAllOperationalHistoryAndIsIdempotent()throws Exception {
        String schema="catalogue_upgrade_"+UUID.randomUUID().toString().replace("-","");
        migration(schema,"17").migrate();
        try(var connection=connection(schema);var statement=connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("insert into zone(id,nom) values(1,'Zone historique')");
            statement.execute("insert into vehicule(id,zone_id) values(1,1)");
            statement.execute("insert into client(id,numero,categorie,actif,zone_id) values(1,reserve_client_number('LOCAL'),'LOCAL',true,1)");
            statement.execute("insert into trajet(id,zone_id,vehicule_id,organisateur_id,depart,arrivee,type,origine,destination) " +
                    "values(1,1,1,1,now()+interval '2 days',now()+interval '2 days 1 hour','PUBLIC','A','B')");
            statement.execute("insert into reservation(id,trajet_id,client_id,statut) values(1,1,1,'CONFIRMEE')");
            statement.execute("insert into paiement_place(reservation_id,cle_idempotence,montant,mode,valide_le) " +
                    "values(1,'"+UUID.randomUUID()+"',20,'SIMULATION',now())");
            statement.execute("insert into dette_trajet(id,trajet_id,client_id,montant) values(1,1,1,80)");
            connection.commit();
        }
        var before=history(schema);
        var upgrade=migration(schema,"18");
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(upgrade.validateWithResult().validationSuccessful).isTrue();
        assertThat(upgrade.migrate().migrationsExecuted).isZero();
        assertThat(history(schema)).containsExactlyElementsOf(before);
        try(var connection=connection(schema);var statement=connection.createStatement();var rows=statement.executeQuery("select count(*) from itineraire_propose")) {
            assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isEqualTo(10);
        }
    }

    @Test void freshMigrationSeedsOnlyReferencesWithoutInventingAnyOperationalData()throws Exception {
        String schema="catalogue_empty_"+UUID.randomUUID().toString().replace("-","");
        migration(schema,"18").migrate();
        assertThat(history(schema)).isEmpty();
        try(var connection=connection(schema);var statement=connection.createStatement();var rows=statement.executeQuery("select count(*) from itineraire_propose")) {
            assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isEqualTo(10);
        }
    }
}
