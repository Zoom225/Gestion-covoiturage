package com.plateforme.covoiturage;

import com.plateforme.covoiturage.enums.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PaymentHistoryIntegrationTests extends ReportingIntegrationSupport {
    static final String HISTORY="/api/paiements/me";

    @Test void historyCombinesOnlyOwnPaymentsAndKeepsPartialDebtPaymentsSeparate()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);reserve(trip,vip);
        paySeat(trip,local);paySeat(trip,vip);
        long debt=generateDebt();payDebt(debt,local,"15.00");payDebt(debt,local,"5.00");
        var response=call("GET",HISTORY,local,null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        var page=read(response);assertThat(page.path("totalElements").asLong()).isEqualTo(3);
        var rows=page.path("content");assertThat(rows.size()).isEqualTo(3);
        assertThat(rows.get(0).path("nature").asText()).isEqualTo("DETTE");
        assertThat(rows.get(1).path("nature").asText()).isEqualTo("DETTE");
        assertThat(rows.get(2).path("nature").asText()).isEqualTo("PLACE");
        assertThat(rows.get(0).path("montant").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(rows.get(1).path("montant").decimalValue()).isEqualByComparingTo("15.00");
        assertThat(rows.get(2).path("montant").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(rows.get(0).path("ressourceId").asLong()).isEqualTo(debt);
        assertThat(rows.get(0).path("valideLe").asText()).isEqualTo(deadline().toString());
        assertThat(rows.get(0).path("mode").asText()).isEqualTo("SIMULATION");
        assertThat(response.body()).doesNotContain("cleIdempotence","password","accessToken","identifiant");
        assertThat(read(call("GET",HISTORY,vip,null)).path("totalElements").asLong()).isEqualTo(1);
        assertThat(read(call("GET",HISTORY,otherLocal,null)).path("totalElements").asLong()).isZero();
    }
    @Test void ownerFilterPrecedesBothPaginationAndCountAndOrderIsStable()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);reserve(trip,vip);paySeat(trip,local);paySeat(trip,vip);
        long debt=generateDebt();payDebt(debt,local,"10.00");payDebt(debt,local,"10.00");
        var expected=read(call("GET",HISTORY,local,null)).path("content");
        var ids=new ArrayList<String>();
        for(int page=0;page<3;page++){
            var result=read(call("GET",HISTORY+"?size=1&page="+page,local,null));
            assertThat(result.path("totalElements").asLong()).isEqualTo(3);
            assertThat(result.path("totalPages").asInt()).isEqualTo(3);
            var item=result.path("content").get(0);
            assertThat(item).isEqualTo(expected.get(page));
            ids.add(item.path("nature").asText()+":"+item.path("paiementId").asLong());
        }
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(read(call("GET",HISTORY+"?size=1&page=3",local,null)).path("content").size()).isZero();
        var restricted=read(call("GET",HISTORY+"?size=1&clientId="+local.getClient().getId(),vip,null));
        assertThat(restricted.path("totalElements").asLong()).isEqualTo(1);
    }
    @Test void emptyHistoryHasZeroTotalAndNoWriteSideEffects()throws Exception{
        long before=seatPayments.count()+debtPayments.count();
        var page=read(call("GET",HISTORY,local,null));
        assertThat(page.path("totalElements").asLong()).isZero();assertThat(page.path("content").size()).isZero();
        assertThat(seatPayments.count()+debtPayments.count()).isEqualTo(before);
    }
    @Test void overlappingIdentifiersAndIdenticalDatesRemainDistinctWithStableNatureOrder()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);paySeat(trip,local);
        long debt=generateDebt();payDebt(debt,local,"20.00");
        long seatId=jdbc.queryForObject("select id from paiement_place",Long.class);
        tx.executeWithoutResult(status->{
            jdbc.update("update paiement_dette set id=?,valide_le=?",seatId,java.sql.Timestamp.from(NOW));
        });
        var page=read(call("GET",HISTORY,local,null));
        assertThat(page.path("totalElements").asLong()).isEqualTo(2);
        var rows=page.path("content");
        assertThat(rows.get(0).path("nature").asText()).isEqualTo("DETTE");
        assertThat(rows.get(1).path("nature").asText()).isEqualTo("PLACE");
        assertThat(rows.get(0).path("paiementId").asLong()).isEqualTo(seatId);
        assertThat(rows.get(1).path("paiementId").asLong()).isEqualTo(seatId);
    }
    @Test void unauthenticatedAdminInvalidAndInactiveTokensAreRejected()throws Exception{
        assertThat(call("GET",HISTORY,null,null).statusCode()).isEqualTo(401);
        assertThat(call("GET",HISTORY,global,null).statusCode()).isEqualTo(403);
        assertThat(call("GET",HISTORY,adminLocal(),null).statusCode()).isEqualTo(403);
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+HISTORY))
                .header("Authorization","Bearer invalid-jwt").GET().build();
        assertThat(http.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        jdbc.update("update compte set actif=false where id=?",local.getId());
        assertThat(call("GET",HISTORY,local,null).statusCode()).isEqualTo(401);
    }
    @ParameterizedTest @ValueSource(strings={"page=-1","size=0","size=101","page=2147483647&size=100","page=abc"})
    void invalidPaginationIs400(String query)throws Exception{
        assertThat(call("GET",HISTORY+"?"+query,local,null).statusCode()).isEqualTo(400);
    }
    @Test void historyDoesNotExposeMutationEndpointsOrOtherOwners()throws Exception{
        assertThat(call("POST",HISTORY,local,null).statusCode()).isEqualTo(403);
        assertThat(call("GET","/api/paiements/"+otherLocal.getClient().getId(),local,null).statusCode()).isEqualTo(403);
    }
}
