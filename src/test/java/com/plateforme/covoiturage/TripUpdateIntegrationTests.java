package com.plateforme.covoiturage;

import com.plateforme.covoiturage.entity.Compte;
import com.plateforme.covoiturage.entity.Vehicule;
import com.plateforme.covoiturage.enums.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/** API sécurisée et PostgreSQL réels, avec l'horloge et les fixtures déjà utilisées par les paiements. */
class TripUpdateIntegrationTests extends ReportingIntegrationSupport {
    private static final Instant START=NOW.plusSeconds(172800);
    private static final Instant END=START.plusSeconds(3600);

    private Map<String,Object> update(Vehicule selected,Instant start,Instant end){
        return new HashMap<>(Map.of("vehiculeId",selected.getId(),"depart",start.toString(),"arrivee",end.toString(),
                "origine","Gare de Bruxelles-Midi","destination","Gare de Liège-Guillemins"));
    }
    private Map<String,Object> update(){return update(secondVehicle,START.plusSeconds(3600),END.plusSeconds(3600));}
    private String path(long trip){return "/api/trajets/"+trip;}
    private List<String> snapshot(){
        var rows=new ArrayList<String>();
        for(String table:List.of("trajet","reservation","paiement_place","paiement_dette","dette_trajet"))
            rows.addAll(jdbc.queryForList("select to_jsonb(t)::text from "+table+" t order by id",String.class));
        return rows;
    }
    private void refused(long trip,Compte actor,Map<String,Object> body,int status,String code)throws Exception{
        var before=snapshot();
        var response=call("PUT",path(trip),actor,body);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(read(response).path("code").asText()).isEqualTo(code);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest @EnumSource(TypeTrajet.class)
    void organizerCanUpdateAndAllPendingReservationsAreKept(TypeTrajet type)throws Exception{
        long trip=create(local,type);
        if(type==TypeTrajet.PUBLIC)reserve(trip,vip);
        else assertThat(call("POST",path(trip)+"/participants",local,Map.of("clientId",vip.getClient().getId())).statusCode()).isEqualTo(201);
        var bookingsBefore=jdbc.queryForList("select to_jsonb(r)::text from reservation r order by id",String.class);
        var response=call("PUT",path(trip),local,update());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        var saved=trips.findById(trip).orElseThrow();
        assertThat(saved.getVehicule().getId()).isEqualTo(secondVehicle.getId());
        assertThat(saved.getDepart()).isEqualTo(START.plusSeconds(3600));
        assertThat(saved.getArrivee()).isEqualTo(END.plusSeconds(3600));
        assertThat(saved.getOrigine()).isEqualTo("Gare de Bruxelles-Midi");
        assertThat(saved.getDestination()).isEqualTo("Gare de Liège-Guillemins");
        assertThat(saved.getZone().getId()).isEqualTo(home.getId());
        assertThat(saved.getOrganisateur().getId()).isEqualTo(local.getClient().getId());
        assertThat(saved.getType()).isEqualTo(type);
        assertThat(saved.getStatut()).isEqualTo(StatutTrajet.PLANIFIE);
        assertThat(jdbc.queryForList("select to_jsonb(r)::text from reservation r order by id",String.class)).isEqualTo(bookingsBefore);
        var detail=read(call("GET",path(trip),local,null));
        assertThat(detail.path("origine").asText()).isEqualTo("Gare de Bruxelles-Midi");
        assertThat(detail.path("depart").asText()).isEqualTo(START.plusSeconds(3600).toString());
        assertThat(seatPayments.count()).isZero();assertThat(debts.count()).isZero();
    }

    @Test void sameVehicleAndSameScheduleDoNotConflictWithTheTripItself()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);
        assertThat(call("PUT",path(trip),local,update(vehicle,START,END)).statusCode()).isEqualTo(200);
    }
    @ParameterizedTest @CsvSource({"origine,false","destination,false","origine,true","destination,true"})
    void pendingReservationLocksEachLocationIncludingTheOrganizersPlace(String field,boolean participant)throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);
        if(participant)reserve(trip,vip);
        var body=update();body.put(field,"Autre gare");
        refused(trip,local,body,409,"TRIP_LOCATIONS_LOCKED");
        assertThat(reservations.countByTrajetIdAndStatut(trip,StatutReservation.EN_ATTENTE)).isEqualTo(participant?2:1);
    }
    @Test void unchangedLocationsRemainAcceptedAfterExistingWhitespaceNormalization()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);reserve(trip,vip);
        var body=update();
        body.put("origine","  Gare de Bruxelles-Midi  ");
        body.put("destination","  Gare de Liège-Guillemins  ");
        assertThat(call("PUT",path(trip),local,body).statusCode()).isEqualTo(200);
        assertThat(trips.findById(trip).orElseThrow().getOrigine()).isEqualTo("Gare de Bruxelles-Midi");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void locationsCanChangeWhenOnlyCancelledReservationsRemain(boolean formerParticipant)throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);
        if(formerParticipant){
            long waiting=reserve(trip,vip);
            assertThat(call("POST","/api/reservations/"+waiting+"/annulation",vip,null).statusCode()).isEqualTo(200);
        }
        assertThat(call("POST","/api/reservations/"+booking(trip,local)+"/annulation",local,null).statusCode()).isEqualTo(200);
        var before=jdbc.queryForList("select to_jsonb(r)::text from reservation r order by id",String.class);
        var body=update();body.put("origine","  Bruxelles-Central  ");body.put("destination","  Namur  ");
        var response=call("PUT",path(trip),local,body);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(trips.findById(trip).orElseThrow().getOrigine()).isEqualTo("Bruxelles-Central");
        assertThat(trips.findById(trip).orElseThrow().getDestination()).isEqualTo("Namur");
        assertThat(jdbc.queryForList("select to_jsonb(r)::text from reservation r order by id",String.class)).isEqualTo(before);
        assertThat(seatPayments.count()).isZero();assertThat(debts.count()).isZero();
    }
    @Test void participantWhoIsNotOrganizerIsForbidden()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);reserve(trip,vip);
        refused(trip,vip,update(),403,"FORBIDDEN");
    }
    @Test void anonymousUserIsRejected()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);
        refused(trip,null,update(),401,"UNAUTHORIZED");
    }
    @Test void administratorCannotReplaceTheOrganizer()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);
        refused(trip,global,update(),403,"FORBIDDEN");
    }
    @ParameterizedTest @ValueSource(longs={0,1})
    void departureReachedOrPassedPreventsUpdate(long seconds)throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);clock.set(START.plusSeconds(seconds));
        refused(trip,local,update(),409,"TRIP_ALREADY_STARTED");
    }
    @ParameterizedTest @EnumSource(value=StatutTrajet.class,names={"ANNULE","REALISE"})
    void closedTripCannotBeUpdated(StatutTrajet status)throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);
        jdbc.update("update trajet set statut=? where id=?",status.name(),trip);
        refused(trip,local,update(),409,"TRIP_NOT_MODIFIABLE");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void anyActiveConfirmationPreventsUpdateIncludingOrganizer(boolean organizer)throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);
        if(!organizer)reserve(trip,vip);
        paySeat(trip,organizer?local:vip);
        refused(trip,local,update(),409,"TRIP_HAS_CONFIRMED_RESERVATION");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void cancelledPaymentStillPreventsUpdateEvenWhenRefunded(boolean refunded)throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);reserve(trip,vip);paySeat(trip,vip);
        if(!refunded)clock.set(START.minusSeconds(23*3600));
        assertThat(call("POST","/api/reservations/"+booking(trip,vip)+"/annulation",vip,null).statusCode()).isEqualTo(200);
        assertThat(reservations.countByTrajetIdAndStatut(trip,StatutReservation.CONFIRMEE)).isZero();
        assertThat(seatPayments.findByReservationId(booking(trip,vip)).orElseThrow().getRembourseLe()!=null).isEqualTo(refunded);
        refused(trip,local,update(),409,"TRIP_HAS_PAYMENT");
    }
    @Test void successfulJ1CheckPreventsEveryUpdate()throws Exception{
        long trip=create(local,TypeTrajet.PRIVE);
        jdbc.update("update trajet set traite_j1_le=? where id=?",Timestamp.from(NOW),trip);
        refused(trip,local,update(),409,"TRIP_J1_ALREADY_PROCESSED");
    }
    @Test void uncertainJ1HistoryPreventsUpdate()throws Exception{
        long trip=create(local,TypeTrajet.PRIVE);
        jdbc.update("update trajet set verification_j1_requise=true where id=?",trip);
        refused(trip,local,update(),409,"TRIP_J1_VERIFICATION_REQUIRED");
    }
    @Test void missedJ1CannotBeAvoidedByMovingTheDeparture()throws Exception{
        long trip=create(local,TypeTrajet.PRIVE);clock.set(deadline());
        refused(trip,local,update(),409,"TRIP_J1_DUE");
        assertThat(j1.catchUp()).isEqualTo(1);
        refused(trip,local,update(),409,"TRIP_J1_ALREADY_PROCESSED");
        assertThat(debts.count()).isEqualTo(1);
    }
    @Test void newPrivateScheduleCannotIntroduceAJ1DeadlineInThePast()throws Exception{
        // Heure fixe pour que le cas soit indépendant de l'heure d'exécution du build.
        var noon=NOW.atZone(java.time.ZoneOffset.UTC).toLocalDate().atTime(12,0).toInstant(java.time.ZoneOffset.UTC);
        clock.set(noon);
        var created=call("POST","/api/trajets",local,request(noon.plusSeconds(172800),noon.plusSeconds(176400),TypeTrajet.PRIVE));
        assertThat(created.statusCode()).isEqualTo(201);
        refused(read(created).path("trajetId").asLong(),local,update(vehicle,noon.plusSeconds(3600),noon.plusSeconds(7200)),409,"TRIP_J1_DUE");
    }
    @Test void organizersActiveDebtOnAnotherTripCannotBeBypassed()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);generateDebt();
        refused(trip,local,update(vehicle,START,END),409,"ACTIVE_DEBT");
    }
    @Test void incompatiblePendingReservationIsKeptAndUpdateIsRefused()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);reserve(trip,occasional);
        // LOCAL : J-10 ; OCCASIONNEL : J-3. Déplacer à J+5 invalide uniquement le participant.
        refused(trip,local,update(vehicle,NOW.plusSeconds(5*86400),NOW.plusSeconds(5*86400+3600)),409,"PENDING_RESERVATION_INCOMPATIBLE");
        assertThat(reservations.findById(booking(trip,occasional)).orElseThrow().getStatut()).isEqualTo(StatutReservation.EN_ATTENTE);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void vehicleOrZoneUnavailabilityPreventsUpdate(boolean wholeZone)throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);
        var start=START.plusSeconds(7200);var end=start.plusSeconds(3600);
        jdbc.update("insert into indisponibilite("+(wholeZone?"zone_id":"vehicule_id")+",debut,fin) values(?,?,?)",
                wholeZone?home.getId():secondVehicle.getId(),Timestamp.from(start),Timestamp.from(end));
        refused(trip,local,update(secondVehicle,start,end),409,"RESOURCE_UNAVAILABLE");
    }
    @Test void inactiveVehiclePreventsUpdate()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);
        jdbc.update("update vehicule set actif=false where id=?",secondVehicle.getId());
        refused(trip,local,update(),409,"VEHICLE_INACTIVE");
    }
    @Test void anotherZoneVehicleCannotBeChosen()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);
        var foreign=vehicles.saveAndFlush(new Vehicule(away));
        refused(trip,local,update(foreign,START,END),409,"VEHICLE_ZONE_MISMATCH");
    }
    @ParameterizedTest @ValueSource(longs={-1,0,899,900})
    void overlapAndFifteenMinuteBoundaryAreCheckedWithoutSelfConflict(long gap)throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);
        long other=createWith(local,home,secondVehicle,TypeTrajet.PUBLIC);
        var start=END.plusSeconds(gap);var body=update(secondVehicle,start,start.plusSeconds(3600));
        if(gap<900)refused(trip,local,body,409,"VEHICLE_CONFLICT");
        else {
            assertThat(call("PUT",path(trip),local,body).statusCode()).isEqualTo(200);
            assertThat(trips.findById(trip).orElseThrow().getDepart()).isEqualTo(start);
        }
        assertThat(trips.findById(other).orElseThrow().getDepart()).isEqualTo(START);
    }
    @ParameterizedTest @ValueSource(strings={"missingVehicle","blankOrigin","longDestination","badInterval","missingDeparture"})
    void invalidRequestDoesNotModifyStoredData(String invalid)throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);var body=update();
        switch(invalid){
            case "missingVehicle"->body.remove("vehiculeId");
            case "blankOrigin"->body.put("origine","   ");
            case "longDestination"->body.put("destination","x".repeat(256));
            case "badInterval"->body.put("arrivee",START.toString());
            case "missingDeparture"->body.remove("depart");
        }
        var before=snapshot();
        assertThat(call("PUT",path(trip),local,body).statusCode()).isEqualTo(400);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void concurrentReservationAndUpdateCannotLeaveAnIneligiblePendingBooking()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);
        var body=update(secondVehicle,NOW.plusSeconds(5*86400),NOW.plusSeconds(5*86400+3600));
        try(var pool=Executors.newFixedThreadPool(2)){
            var go=new CountDownLatch(1);
            var edit=pool.submit(()->{go.await();return call("PUT",path(trip),local,body);});
            var reserve=pool.submit(()->{go.await();return call("POST",path(trip)+"/reservations",occasional,null);});
            go.countDown();
            var editResult=edit.get(30,TimeUnit.SECONDS);var bookingResult=reserve.get(30,TimeUnit.SECONDS);
            if(editResult.statusCode()==200){
                assertThat(bookingResult.statusCode()).as(bookingResult.body()).isEqualTo(409);
                assertThat(read(bookingResult).path("code").asText()).isEqualTo("BOOKING_HORIZON");
                assertThat(reservations.countByTrajetIdAndStatut(trip,StatutReservation.EN_ATTENTE)).isEqualTo(1);
            }else{
                assertThat(editResult.statusCode()).as(editResult.body()).isEqualTo(409);
                assertThat(read(editResult).path("code").asText()).isEqualTo("PENDING_RESERVATION_INCOMPATIBLE");
                assertThat(bookingResult.statusCode()).as(bookingResult.body()).isEqualTo(201);
                assertThat(trips.findById(trip).orElseThrow().getDepart()).isEqualTo(START);
            }
        }
        assertThat(seatPayments.count()).isZero();
    }
    @Test void concurrentPaymentAndUpdateAreSerialized()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);long booking=reserve(trip,vip);
        try(var pool=Executors.newFixedThreadPool(2)){
            var go=new CountDownLatch(1);
            var edit=pool.submit(()->{go.await();return call("PUT",path(trip),local,update());});
            var payment=pool.submit(()->{go.await();return pay("/api/reservations/"+booking+"/paiements",vip,UUID.randomUUID(),null);});
            go.countDown();
            var editResult=edit.get(30,TimeUnit.SECONDS);var paid=payment.get(30,TimeUnit.SECONDS);
            assertThat(paid.statusCode()).as(paid.body()).isEqualTo(200);
            assertThat(editResult.statusCode()).as(editResult.body()).isIn(200,409);
            var saved=trips.findById(trip).orElseThrow();
            if(editResult.statusCode()==200){
                assertThat(saved.getDepart()).isEqualTo(START.plusSeconds(3600));
                assertThat(saved.getVehicule().getId()).isEqualTo(secondVehicle.getId());
            }else{
                assertThat(read(editResult).path("code").asText()).isEqualTo("TRIP_HAS_CONFIRMED_RESERVATION");
                assertThat(saved.getDepart()).isEqualTo(START);
                assertThat(saved.getVehicule().getId()).isEqualTo(vehicle.getId());
            }
        }
        assertThat(reservations.findById(booking).orElseThrow().getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
        assertThat(seatPayments.count()).isEqualTo(1);
        refused(trip,local,update(),409,"TRIP_HAS_CONFIRMED_RESERVATION");
    }
}
