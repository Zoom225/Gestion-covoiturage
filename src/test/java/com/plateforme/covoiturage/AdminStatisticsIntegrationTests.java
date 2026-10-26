package com.plateforme.covoiturage;

import com.plateforme.covoiturage.entity.*;
import com.plateforme.covoiturage.enums.*;
import com.plateforme.covoiturage.service.ReferentielService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import java.net.URI;
import java.net.http.*;
import static org.assertj.core.api.Assertions.*;

class AdminStatisticsIntegrationTests extends ReportingIntegrationSupport {
    static final String STATS="/api/admin/statistiques";
    @Autowired ReferentielService service;

    @Test void aggregatesUseTripZoneAndDoNotMultiplyAmountsThroughJoins()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);reserve(trip,vip);paySeat(trip,local);paySeat(trip,vip);
        var awayVehicle=vehicles.saveAndFlush(new Vehicule(away));
        long awayTrip=createWith(otherLocal,away,awayVehicle,TypeTrajet.PUBLIC);paySeat(awayTrip,otherLocal);
        long debt=generateDebt();payDebt(debt,local,"15.00");payDebt(debt,local,"5.00");
        var all=call("GET",STATS,global,null);assertThat(all.statusCode()).isEqualTo(200);
        assertThat(all.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        var total=read(all);
        assertThat(total.path("trajetsPlanifies").asLong()).isEqualTo(3);
        assertThat(total.path("reservationsConfirmees").asLong()).isEqualTo(3);
        assertThat(total.path("reservationsEnAttente").asLong()).isEqualTo(1);
        assertThat(total.path("paiementsPlaces").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(total.path("paiementsDettes").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(total.path("dettesRestantes").decimalValue()).isEqualByComparingTo("60.00");
        var own=read(call("GET",STATS,adminLocal(),null));
        assertThat(own.path("trajetsPlanifies").asLong()).isEqualTo(2);
        assertThat(own.path("reservationsConfirmees").asLong()).isEqualTo(2);
        assertThat(own.path("paiementsPlaces").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(own.path("paiementsDettes").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(own).isEqualTo(read(call("GET",STATS+"?zoneId="+home.getId(),global,null)));
        var outside=read(call("GET",STATS+"?zoneId="+away.getId(),global,null));
        assertThat(outside.path("trajetsPlanifies").asLong()).isEqualTo(1);
        assertThat(outside.path("paiementsPlaces").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(outside.path("dettesRestantes").decimalValue()).isEqualByComparingTo("0");
        assertThat(all.body()).doesNotContain("clientId","password","accessToken","identifiant","cleIdempotence");
    }
    @Test void localFilterCannotExpandScopeAndUnknownZoneIsEmpty()throws Exception{
        create(local,TypeTrajet.PUBLIC);var actor=adminLocal();
        assertThat(read(call("GET",STATS,actor,null)).path("trajetsPlanifies").asLong()).isEqualTo(1);
        for(long zone:new long[]{away.getId(),Long.MAX_VALUE}){
            var response=call("GET",STATS+"?zoneId="+zone,actor,null);
            assertThat(response.statusCode()).isEqualTo(200);
            read(response).properties().forEach(entry->assertThat(entry.getValue().decimalValue()).isEqualByComparingTo("0"));
        }
    }
    @Test void emptyNetworkHasZeroCountersAndDecimalTotals()throws Exception{
        var response=call("GET",STATS,global,null);assertThat(response.statusCode()).isEqualTo(200);
        assertThat(read(response).size()).isEqualTo(9);
        read(response).properties().forEach(entry->assertThat(entry.getValue().decimalValue()).isEqualByComparingTo("0"));
    }
    @Test void fullCancelledAndReservationStatesAreCountedSeparately()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);reserve(trip,vip);reserve(trip,occasional);
        var fourth=account("O970051",CategorieClient.OCCASIONNEL,null);reserve(trip,fourth);
        for(var actor:java.util.List.of(local,vip,occasional,fourth))paySeat(trip,actor);
        long cancelled=createWith(vip,home,secondVehicle,TypeTrajet.PUBLIC);
        tx.executeWithoutResult(status->{
            jdbc.update("update reservation set statut='ANNULEE' where trajet_id=?",cancelled);
            jdbc.update("update trajet set statut='ANNULE' where id=?",cancelled);
        });
        var total=read(call("GET",STATS,global,null));
        assertThat(total.path("trajetsComplets").asLong()).isEqualTo(1);
        assertThat(total.path("trajetsAnnules").asLong()).isEqualTo(1);
        assertThat(total.path("trajetsPlanifies").asLong()).isZero();
        assertThat(total.path("reservationsConfirmees").asLong()).isEqualTo(4);
        assertThat(total.path("reservationsAnnulees").asLong()).isEqualTo(1);
        assertThat(total.path("paiementsPlaces").decimalValue()).isEqualByComparingTo("80.00");
    }
    @Test void fullySettledDebtContributesPaymentsButNoOutstandingAmount()throws Exception{
        long debt=generateDebt();payDebt(debt,local,"30.00");payDebt(debt,local,"50.00");
        var total=read(call("GET",STATS,global,null));
        assertThat(total.path("paiementsDettes").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(total.path("dettesRestantes").decimalValue()).isEqualByComparingTo("0");
    }
    @Test void securityIsEnforcedOverHttpAndInsideService()throws Exception{
        assertThat(call("GET",STATS,null,null).statusCode()).isEqualTo(401);
        assertThat(call("GET",STATS,local,null).statusCode()).isEqualTo(403);
        var actor=new UsernamePasswordAuthenticationToken(auth.resolvePrincipal(local.getId().toString()),null,java.util.List.of());
        assertThatThrownBy(()->service.statistics(actor,null)).isInstanceOf(AccessDeniedException.class);
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+STATS))
                .header("Authorization","Bearer invalid").GET().build();
        assertThat(http.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }
    @ParameterizedTest @ValueSource(strings={"-1","0","abc"})
    void invalidZoneFilterIs400(String zone)throws Exception{
        assertThat(call("GET",STATS+"?zoneId="+zone,global,null).statusCode()).isEqualTo(400);
    }
}
