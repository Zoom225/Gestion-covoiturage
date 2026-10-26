package com.plateforme.covoiturage;

import com.plateforme.covoiturage.entity.*;
import com.plateforme.covoiturage.enums.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.flywaydb.core.Flyway;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class FleetManagementIntegrationTests extends ReportingIntegrationSupport {
    Instant start(){ return NOW.plusSeconds(172800); }
    Instant end(){ return start().plusSeconds(3600); }
    Map<String,Object> period(boolean zone,Instant from,Instant until){
        return Map.of(zone?"zoneId":"vehiculeId",zone?home.getId():vehicle.getId(),
            "debut",from.toString(),"fin",until.toString());
    }
    long newPeriod(boolean zone,Instant from,Instant until)throws Exception{
        var r=call("POST","/api/admin/indisponibilites",global,period(zone,from,until));
        assertThat(r.statusCode()).isEqualTo(201);
        assertThat(r.headers().firstValue("Location")).contains("/api/admin/indisponibilites/"+read(r).path("indisponibiliteId").asLong());
        return read(r).path("indisponibiliteId").asLong();
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void authorizedAdministratorsCreateVehicles(boolean localAdmin)throws Exception{
        var actor=localAdmin?adminLocal():global;
        var r=call("POST","/api/admin/vehicules",actor,Map.of("zoneId",home.getId()));
        assertThat(r.statusCode()).isEqualTo(201);
        long id=read(r).path("vehiculeId").asLong();
        assertThat(read(r).path("actif").asBoolean()).isTrue();
        assertThat(read(r).path("zoneId").asLong()).isEqualTo(home.getId());
        assertThat(r.headers().firstValue("Location")).contains("/api/admin/vehicules/"+id);
        assertThat(vehicles.findById(id)).isPresent();
    }
    @Test void globalCreatesInAnotherZoneButLocalCannot()throws Exception{
        assertThat(call("POST","/api/admin/vehicules",global,Map.of("zoneId",away.getId())).statusCode()).isEqualTo(201);
        assertThat(call("POST","/api/admin/vehicules",adminLocal(),Map.of("zoneId",away.getId())).statusCode()).isEqualTo(404);
        assertThat(call("POST","/api/admin/vehicules",global,Map.of("zoneId",Long.MAX_VALUE)).statusCode()).isEqualTo(404);
    }
    @ParameterizedTest @ValueSource(strings={"POST:/vehicules","PATCH:/vehicules/1/statut","POST:/indisponibilites","PUT:/indisponibilites/1","DELETE:/indisponibilites/1"})
    void everyMutationRequiresAdministrativeJwt(String route)throws Exception{
        var parts=route.split(":",2);
        assertThat(call(parts[0],"/api/admin"+parts[1],null,Map.of()).statusCode()).isEqualTo(401);
        assertThat(call(parts[0],"/api/admin"+parts[1],local,Map.of()).statusCode()).isEqualTo(403);
    }
    @Test void localCannotMutateVehiclesOrPeriodsOutsideScope()throws Exception{
        var foreign=vehicles.saveAndFlush(new Vehicule(away));
        long id=jdbc.queryForObject("insert into indisponibilite(zone_id,debut,fin) values(?,?,?) returning id",Long.class,
            away.getId(),Timestamp.from(start()),Timestamp.from(end()));
        var actor=adminLocal();
        assertThat(call("PATCH","/api/admin/vehicules/"+foreign.getId()+"/statut",actor,Map.of("actif",false)).statusCode()).isEqualTo(404);
        assertThat(call("POST","/api/admin/indisponibilites",actor,Map.of("vehiculeId",foreign.getId(),"debut",start().toString(),"fin",end().toString())).statusCode()).isEqualTo(404);
        assertThat(call("PUT","/api/admin/indisponibilites/"+id,actor,Map.of("zoneId",away.getId(),"debut",start().toString(),"fin",end().toString())).statusCode()).isEqualTo(404);
        assertThat(call("DELETE","/api/admin/indisponibilites/"+id,actor,null).statusCode()).isEqualTo(404);
        assertThat(vehicles.findById(foreign.getId()).orElseThrow().isActif()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from indisponibilite where id=?",Long.class,id)).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"neither","both","zero","negative","equal","reverse","missingStart","missingEnd"})
    void malformedPeriodsReturn400WithoutWrites(String kind)throws Exception{
        var input=new HashMap<String,Object>(period(true,start(),end()));
        switch(kind){
            case "neither"->input.remove("zoneId");
            case "both"->input.put("vehiculeId",vehicle.getId());
            case "zero"->input.put("zoneId",0);
            case "negative"->input.put("zoneId",-1);
            case "equal"->input.put("fin",start().toString());
            case "reverse"->input.put("fin",start().minusSeconds(1).toString());
            case "missingStart"->input.remove("debut");
            case "missingEnd"->input.remove("fin");
        }
        assertThat(call("POST","/api/admin/indisponibilites",global,input).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("select count(*) from indisponibilite",Long.class)).isZero();
    }
    @Test void mandatoryVehicleFieldsAreValidated()throws Exception{
        assertThat(call("POST","/api/admin/vehicules",global,Map.of()).statusCode()).isEqualTo(400);
        assertThat(call("PATCH","/api/admin/vehicules/"+vehicle.getId()+"/statut",global,Map.of()).statusCode()).isEqualTo(400);
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void localCanCreateUpdateAndDeletePeriodsWithFixedTargets(boolean zone)throws Exception{
        var actor=adminLocal();
        var r=call("POST","/api/admin/indisponibilites",actor,period(zone,start(),end()));
        assertThat(r.statusCode()).isEqualTo(201);
        long id=read(r).path("indisponibiliteId").asLong();
        assertThat(call("PUT","/api/admin/indisponibilites/"+id,actor,period(zone,end(),end().plusSeconds(3600))).statusCode()).isEqualTo(200);
        assertThat(call("PUT","/api/admin/indisponibilites/"+id,actor,period(!zone,end(),end().plusSeconds(3600))).statusCode()).isEqualTo(409);
        assertThat(call("DELETE","/api/admin/indisponibilites/"+id,actor,null).statusCode()).isEqualTo(204);
        assertThat(call("GET","/api/admin/indisponibilites/"+id,actor,null).statusCode()).isEqualTo(404);
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void existingTripsPreventCreatingOrMovingPeriods(boolean zone)throws Exception{
        create(local,TypeTrajet.PUBLIC);
        assertThat(call("POST","/api/admin/indisponibilites",global,period(zone,start(),end())).statusCode()).isEqualTo(409);
        long id=newPeriod(zone,end(),end().plusSeconds(3600));
        assertThat(call("PUT","/api/admin/indisponibilites/"+id,global,period(zone,start(),end())).statusCode()).isEqualTo(409);
        assertThat(call("GET","/api/admin/indisponibilites/"+id,global,null).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select debut from indisponibilite where id=?",Timestamp.class,id).toInstant()).isEqualTo(end());
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void halfOpenPeriodBoundariesRemainValid(boolean zone)throws Exception{
        create(local,TypeTrajet.PUBLIC);
        newPeriod(zone,start().minusSeconds(3600),start());
        newPeriod(zone,end(),end().plusSeconds(3600));
        assertThat(call("POST","/api/admin/indisponibilites",global,period(zone,start().minusSeconds(1),start().plusSeconds(1))).statusCode()).isEqualTo(409);
    }
    @Test void disablingOnlyBlocksNewPlanningAndPreservesExistingPayments()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);
        var actor=adminLocal();
        assertThat(call("PATCH","/api/admin/vehicules/"+vehicle.getId()+"/statut",actor,Map.of("actif",false)).statusCode()).isEqualTo(200);
        assertThat(trips.findById(trip).orElseThrow().getStatut()).isEqualTo(StatutTrajet.PLANIFIE);
        long booking=reserve(trip,vip);
        assertThat(pay("/api/reservations/"+booking+"/paiements",vip,UUID.randomUUID(),null).statusCode()).isEqualTo(200);
        assertThat(reservations.findById(booking).orElseThrow().getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
        var next=request(end().plusSeconds(900),end().plusSeconds(4500),TypeTrajet.PUBLIC);
        var denied=call("POST","/api/trajets",local,next);
        assertThat(denied.statusCode()).isEqualTo(409);
        assertThat(read(denied).path("code").asText()).isEqualTo("VEHICLE_INACTIVE");
        assertThat(call("PATCH","/api/admin/vehicules/"+vehicle.getId()+"/statut",actor,Map.of("actif",true)).statusCode()).isEqualTo(200);
        assertThat(call("POST","/api/trajets",local,next).statusCode()).isEqualTo(201);
    }
    @Test void deactivationIsIdempotentAndExposedToClients()throws Exception{
        for(int i=0;i<2;i++) assertThat(call("PATCH","/api/admin/vehicules/"+vehicle.getId()+"/statut",global,Map.of("actif",false)).statusCode()).isEqualTo(200);
        var page=read(call("GET","/api/vehicules",local,null));
        assertThat(page.path("content").findValues("actif")).isNotEmpty();
        assertThat(jdbc.queryForObject("select actif from vehicule where id=?",Boolean.class,vehicle.getId())).isFalse();
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void postgresRejectsPeriodInsertAndUpdateAgainstTrips(boolean zone)throws Exception{
        create(local,TypeTrajet.PUBLIC);
        String column=zone?"zone_id":"vehicule_id";long target=zone?home.getId():vehicle.getId();
        assertThatThrownBy(()->jdbc.update("insert into indisponibilite("+column+",debut,fin) values(?,?,?)",target,Timestamp.from(start()),Timestamp.from(end())))
            .isInstanceOf(DataIntegrityViolationException.class);
        long id=newPeriod(zone,end(),end().plusSeconds(3600));
        assertThatThrownBy(()->jdbc.update("update indisponibilite set debut=?,fin=? where id=?",Timestamp.from(start()),Timestamp.from(end()),id))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
    long sqlTrip(Connection c,Instant from,Instant until)throws SQLException{
        try(var s=c.prepareStatement("insert into trajet(zone_id,vehicule_id,organisateur_id,depart,arrivee,type,origine,destination) values(?,?,?,?,?,'PUBLIC','Origine','Destination') returning id")){
            s.setLong(1,home.getId());s.setLong(2,vehicle.getId());s.setLong(3,local.getClient().getId());
            s.setTimestamp(4,Timestamp.from(from));s.setTimestamp(5,Timestamp.from(until));
            try(var r=s.executeQuery()){r.next();return r.getLong(1);}
        }
    }
    void sqlPeriod(Connection c,boolean zone,Instant from,Instant until)throws SQLException{
        try(var s=c.prepareStatement("insert into indisponibilite("+(zone?"zone_id":"vehicule_id")+",debut,fin) values(?,?,?)")){
            s.setLong(1,zone?home.getId():vehicle.getId());s.setTimestamp(2,Timestamp.from(from));s.setTimestamp(3,Timestamp.from(until));s.executeUpdate();
        }
    }
    @Test void postgresBlocksInactiveNewPlanningAndReschedulingButNotStatusUpdates()throws Exception{
        long id=create(local,TypeTrajet.PUBLIC);
        jdbc.update("update vehicule set actif=false where id=?",vehicle.getId());
        try(var c=dataSource.getConnection()){
            assertThatThrownBy(()->sqlTrip(c,end().plusSeconds(900),end().plusSeconds(4500))).isInstanceOf(SQLException.class);
        }
        assertThatThrownBy(()->jdbc.update("update trajet set depart=depart+interval '1 day',arrivee=arrivee+interval '1 day' where id=?",id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update("update trajet set type='PRIVE' where id=?",id)).isEqualTo(1);
    }
    @Test void postgresBlocksMovingTripIntoUnavailablePeriod()throws Exception{
        long id=create(local,TypeTrajet.PUBLIC);newPeriod(true,end().plusSeconds(900),end().plusSeconds(4500));
        assertThatThrownBy(()->jdbc.update("update trajet set depart=?,arrivee=? where id=?",
            Timestamp.from(end().plusSeconds(900)),Timestamp.from(end().plusSeconds(4500)),id)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void postgresValidatesNewPeriodTargetOnReassignment(boolean zone)throws Exception{
        create(local,TypeTrajet.PUBLIC);
        long id=jdbc.queryForObject("insert into indisponibilite(zone_id,debut,fin) values(?,?,?) returning id",Long.class,
            away.getId(),Timestamp.from(start()),Timestamp.from(end()));
        assertThatThrownBy(()->jdbc.update(zone?"update indisponibilite set zone_id=? where id=?":
            "update indisponibilite set zone_id=null,vehicule_id=? where id=?",zone?home.getId():vehicle.getId(),id))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void concurrentHttpTripAndPeriodCannotBothSucceed(boolean zone)throws Exception{
        var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var a=pool.submit(()->{gate.await();return call("POST","/api/trajets",local,request(start(),end(),TypeTrajet.PUBLIC)).statusCode();});
            var b=pool.submit(()->{gate.await();return call("POST","/api/admin/indisponibilites",global,period(zone,start(),end())).statusCode();});
            gate.countDown();assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
            assertThat(jdbc.queryForObject("select count(*) from trajet",Long.class)
                +jdbc.queryForObject("select count(*) from indisponibilite",Long.class)).isEqualTo(1);
        }finally{pool.shutdownNow();assertThat(pool.awaitTermination(30,TimeUnit.SECONDS)).isTrue();}
    }
    @ParameterizedTest @ValueSource(strings={"trip-zone","trip-vehicle","period-zone","period-vehicle"})
    void postgresSerializesBothOrdersOfConcurrentDirectWrites(String kind)throws Exception{
        boolean tripFirst=kind.startsWith("trip");boolean zone=kind.endsWith("zone");
        var pool=Executors.newSingleThreadExecutor();
        try(var first=dataSource.getConnection();var second=dataSource.getConnection()){
            first.setAutoCommit(false);
            int pid;try(var s=second.createStatement();var r=s.executeQuery("select pg_backend_pid()")){r.next();pid=r.getInt(1);}
            if(tripFirst)sqlTrip(first,start(),end());else sqlPeriod(first,zone,start(),end());
            var waiter=pool.submit(()->{try{
                if(tripFirst)sqlPeriod(second,zone,start(),end());else sqlTrip(second,start(),end());
                return "ACCEPTED";
            }catch(SQLException ex){return ex.getSQLState();}});
            try{
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean waiting=false;
                while(System.nanoTime()<until){
                    waiting=Boolean.TRUE.equals(jdbc.queryForObject("select wait_event_type='Lock' from pg_stat_activity where pid=?",Boolean.class,pid));
                    if(waiting)break;Thread.sleep(20);
                }
                assertThat(waiting).as("second writer waits for resource lock").isTrue();
                first.commit();
                assertThat(waiter.get(15,TimeUnit.SECONDS)).isEqualTo("23514");
            }finally{first.rollback();}
        }finally{pool.shutdownNow();assertThat(pool.awaitTermination(30,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void swaggerDocumentsFleetMutationsAndActivity()throws Exception{
        var schema=read(call("GET","/v3/api-docs",null,null));
        assertThat(schema.path("paths").path("/api/admin/vehicules").has("post")).isTrue();
        assertThat(schema.path("paths").path("/api/admin/indisponibilites/{id}").has("put")).isTrue();
        assertThat(schema.path("components").path("schemas").path("VehiculeResponse").path("properties").has("actif")).isTrue();
    }
}
