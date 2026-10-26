package com.plateforme.covoiturage;

import com.plateforme.covoiturage.entity.*;
import com.plateforme.covoiturage.enums.*;
import com.plateforme.covoiturage.repository.*;
import com.plateforme.covoiturage.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@TestPropertySource(properties="driveconnect.scheduling.j1.enabled=false")
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
abstract class ReportingIntegrationSupport extends JourneyIntegrationSupport {
    @Autowired J1ProcessingService j1;
    @Autowired DetteTrajetRepository debts;
    @Autowired PaiementPlaceRepository seatPayments;
    @Autowired PaiementDetteRepository debtPayments;
    @Autowired AuthService auth;
    @Override void cleanBusiness(){
        tx.executeWithoutResult(status->{
            jdbc.update("delete from paiement_place");jdbc.update("delete from paiement_dette");super.cleanBusiness();
        });
    }
    HttpResponse<String> pay(String path,Compte actor,UUID key,Object body)throws Exception{
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
                .header("Content-Type","application/json").header("Idempotency-Key",key.toString())
                .header("Authorization","Bearer "+new TokenService(encoder,jwtProperties,Clock.systemUTC()).issue(actor.getId()).accessToken());
        return http.send(request.POST(HttpRequest.BodyPublishers.ofString(body==null?"":json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    long booking(long trip,Compte actor){
        return jdbc.queryForObject("select id from reservation where trajet_id=? and client_id=?",Long.class,trip,actor.getClient().getId());
    }
    void paySeat(long trip,Compte actor)throws Exception{
        assertThat(pay("/api/reservations/"+booking(trip,actor)+"/paiements",actor,UUID.randomUUID(),null).statusCode()).isEqualTo(200);
    }
    long reserve(long trip,Compte actor)throws Exception{
        var response=call("POST","/api/trajets/"+trip+"/reservations",actor,null);
        assertThat(response.statusCode()).isEqualTo(201);return read(response).path("reservationId").asLong();
    }
    long createWith(Compte actor,Zone zone,Vehicule chosen,TypeTrajet type)throws Exception{
        var body=new HashMap<String,Object>(request(NOW.plusSeconds(172800),NOW.plusSeconds(176400),type));
        body.put("zoneId",zone.getId());body.put("vehiculeId",chosen.getId());
        var response=call("POST","/api/trajets",actor,body);
        assertThat(response.statusCode()).isEqualTo(201);return read(response).path("trajetId").asLong();
    }
    Instant deadline(){
        return NOW.plusSeconds(172800).atZone(ZoneId.of("Europe/Brussels")).toLocalDate().minusDays(1)
                .atTime(0,15).atZone(ZoneId.of("Europe/Brussels")).toInstant();
    }
    long generateDebt()throws Exception{
        long trip=createWith(local,home,secondVehicle,TypeTrajet.PRIVE);
        clock.set(deadline());assertThat(j1.catchUp()).isEqualTo(1);
        return jdbc.queryForObject("select id from dette_trajet where trajet_id=?",Long.class,trip);
    }
    void payDebt(long debt,Compte actor,String amount)throws Exception{
        assertThat(pay("/api/dettes/"+debt+"/paiements",actor,UUID.randomUUID(),Map.of("montant",amount)).statusCode()).isEqualTo(200);
    }
    Compte adminLocal(){
        return accounts.saveAndFlush(new Compte("zone@reporting.test",hash,true,RoleTechnique.ADMIN,null,PerimetreAdmin.LOCAL,home));
    }
}
