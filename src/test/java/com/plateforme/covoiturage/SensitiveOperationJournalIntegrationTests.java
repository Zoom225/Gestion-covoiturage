package com.plateforme.covoiturage;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.plateforme.covoiturage.dto.request.ZoneWriteRequest;
import com.plateforme.covoiturage.enums.*;
import com.plateforme.covoiturage.service.*;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SensitiveOperationJournalIntegrationTests extends ReportingIntegrationSupport {
    @Autowired SensitiveOperationJournal journal;
    @Autowired PaymentService payments;
    @Autowired ReferentielService references;
    @Autowired InvitationService invitations;
    @MockitoBean InvitationDeliveryService delivery;
    final Logger logger=(Logger)LoggerFactory.getLogger(SensitiveOperationJournal.class);
    ListAppender<ILoggingEvent> appender;
    @BeforeEach void captureJournal(){
        appender=new ListAppender<>();appender.start();logger.addAppender(appender);
    }
    @AfterEach void detachJournal(){logger.detachAppender(appender);appender.stop();}
    List<String> messages(){return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();}
    long actions(String action){return messages().stream().filter(m->m.contains("action="+action+" ")).count();}
    org.springframework.security.core.Authentication actor(com.plateforme.covoiturage.entity.Compte account){
        return new UsernamePasswordAuthenticationToken(auth.resolvePrincipal(account.getId().toString()),null,List.of());
    }
    @Test void successfulPaymentIsTracedOnceAndIdempotentReplayAddsNothing()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);long booking=booking(trip,local);UUID key=UUID.randomUUID();
        String path="/api/reservations/"+booking+"/paiements";
        assertThat(pay(path,local,key,null).statusCode()).isEqualTo(200);
        assertThat(actions("PLACE_PAYEE")).isEqualTo(1);assertThat(actions("RESERVATION_CONFIRMEE")).isEqualTo(1);
        assertThat(pay(path,local,key,null).statusCode()).isEqualTo(200);
        assertThat(actions("PLACE_PAYEE")).isEqualTo(1);assertThat(actions("RESERVATION_CONFIRMEE")).isEqualTo(1);
        assertThat(String.join("\n",messages())).contains("acteur="+local.getId(),"date="+NOW)
                .doesNotContain(key.toString(),hash,global.getIdentifiant(),"Bearer","password","cleIdempotence");
    }
    @Test void rollbackAfterPaymentNeverLogsSuccessAndRollsBackPaymentAndConfirmation()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);long booking=booking(trip,local);
        assertThatThrownBy(()->tx.executeWithoutResult(status->{
            payments.paySeat(actor(local),booking,UUID.randomUUID());
            assertThat(messages()).isEmpty();
            throw new IllegalStateException("rollback volontaire");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(messages()).isEmpty();assertThat(seatPayments.count()).isZero();
        assertThat(reservations.findById(booking).orElseThrow().getStatut()).isEqualTo(StatutReservation.EN_ATTENTE);
    }
    @Test void deferredPostgresFailureAtCommitDoesNotProduceFalseSuccess()throws Exception{
        long debt=generateDebt();appender.list.clear();
        assertThatThrownBy(()->tx.executeWithoutResult(status->{
            journal.record(SensitiveOperationJournal.Action.DETTE_REGLEE,actor(local),SensitiveOperationJournal.Resource.DETTE,debt);
            jdbc.update("update dette_trajet set regle=1 where id=?",debt);
            assertThat(messages()).isEmpty();
        })).isInstanceOf(RuntimeException.class);
        assertThat(messages()).isEmpty();assertThat(debts.findById(debt).orElseThrow().getRegle()).isZero();
    }
    @Test void j1LogsCommittedConversionAndDebtOnceWithExplicitSystemActor()throws Exception{
        long debt=generateDebt();
        assertThat(actions("TRAJET_CONVERTI_PUBLIC")).isEqualTo(1);assertThat(actions("DETTE_CREEE")).isEqualTo(1);
        assertThat(messages()).allMatch(m->m.contains("acteur=SYSTEME"));
        assertThat(j1.catchUp()).isZero();assertThat(actions("DETTE_CREEE")).isEqualTo(1);
        payDebt(debt,local,"20.00");assertThat(actions("DETTE_REGULARISEE")).isZero();
        payDebt(debt,local,"60.00");assertThat(actions("DETTE_REGULARISEE")).isEqualTo(1);
        assertThat(actions("DETTE_REGLEE")).isEqualTo(2);
    }
    @Test void fourthPaymentLogsFullStateExactlyOnce()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);reserve(trip,vip);reserve(trip,occasional);
        var fourth=account("O970061",CategorieClient.OCCASIONNEL,null);reserve(trip,fourth);
        for(var account:List.of(local,vip,occasional,fourth))paySeat(trip,account);
        assertThat(actions("TRAJET_COMPLET")).isEqualTo(1);
    }
    @Test void administrativeMutationsAreTracedWithoutUserTextAndNoopsAreNotRepeated()throws Exception{
        String secretText="nom-confidentiel@example.test";
        var created=call("POST","/api/admin/zones",global,Map.of("nom",secretText,"fuseauHoraire","Europe/Brussels","ouverteOccasionnels",false));
        assertThat(created.statusCode()).isEqualTo(201);long id=read(created).path("zoneId").asLong();
        assertThat(actions("ZONE_CREEE")).isEqualTo(1);
        assertThat(call("PUT","/api/admin/zones/"+id,global,Map.of("nom","Nouveau nom","fuseauHoraire","Europe/Paris","ouverteOccasionnels",true)).statusCode()).isEqualTo(200);
        assertThat(actions("ZONE_CONFIGUREE")).isEqualTo(1);
        for(int i=0;i<2;i++)assertThat(call("PATCH","/api/admin/zones/"+id+"/statut",global,Map.of("actif",false)).statusCode()).isEqualTo(200);
        assertThat(actions("ZONE_DESACTIVEE")).isEqualTo(1);
        assertThat(call("PATCH","/api/admin/zones/"+id+"/statut",global,Map.of("actif",true)).statusCode()).isEqualTo(200);
        assertThat(actions("ZONE_ACTIVEE")).isEqualTo(1);
        assertThat(String.join("\n",messages())).contains("acteur="+global.getId()).doesNotContain(secretText,"Nouveau nom",global.getIdentifiant());
    }
    @Test void vipAuthorizationLogContainsBothTechnicalReferencesAndSkipsNoops()throws Exception{
        String path="/api/admin/zones/"+home.getId()+"/clients-vip/"+ungrantedVip.getClient().getId();
        for(int i=0;i<2;i++)assertThat(call("PUT",path,global,null).statusCode()).isEqualTo(204);
        assertThat(actions("AUTORISATION_VIP_ACCORDEE")).isEqualTo(1);
        for(int i=0;i<2;i++)assertThat(call("DELETE",path,global,null).statusCode()).isEqualTo(204);
        assertThat(actions("AUTORISATION_VIP_RETIREE")).isEqualTo(1);
        assertThat(messages()).allMatch(m->m.contains("id="+home.getId()+" ")&&m.contains("reference="+ungrantedVip.getClient().getId()+" "));
    }
    @Test void administrativeRollbackAndUnauthorizedRequestsProduceNoSuccess()throws Exception{
        assertThatThrownBy(()->tx.executeWithoutResult(status->{
            references.createZone(actor(global),new ZoneWriteRequest("Zone non validée","Europe/Brussels",false));
            assertThat(messages()).isEmpty();status.setRollbackOnly();throw new IllegalStateException("abandon");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(messages()).isEmpty();
        assertThat(call("PATCH","/api/admin/zones/"+home.getId()+"/statut",adminLocal(),Map.of("actif",false)).statusCode()).isEqualTo(403);
        assertThat(messages()).isEmpty();
    }
    @Test void accountCreationRenewalAndActivationAreTracedWithoutInvitationOrPassword()throws Exception{
        String contact="secret-contact@journal.test";
        var created=call("POST","/api/admin/clients",global,Map.of("email",contact,"categorie","LOCAL","zoneClientId",home.getId()));
        assertThat(created.statusCode()).isEqualTo(201);long id=read(created).path("compte").path("compteId").asLong();
        assertThat(actions("COMPTE_CREE")).isEqualTo(1);
        assertThat(call("POST","/api/admin/comptes/"+id+"/invitations",global,null).statusCode()).isEqualTo(202);
        assertThat(actions("INVITATION_RENOUVELEE")).isEqualTo(1);
        var issued=invitations.renouveler(id);String password=UUID.randomUUID().toString();
        assertThat(call("POST","/api/auth/invitations/accept",null,Map.of("jeton",issued.jeton(),"password",password)).statusCode()).isEqualTo(204);
        assertThat(actions("COMPTE_ACTIVE")).isEqualTo(1);
        assertThat(String.join("\n",messages())).contains("action=COMPTE_ACTIVE acteur="+id)
                .doesNotContain(contact,issued.jeton(),password,hash,"Bearer");
    }
    @Test void readsAndRejectedPaymentsDoNotGenerateMutationEvents()throws Exception{
        assertThat(call("GET","/api/paiements/me",local,null).statusCode()).isEqualTo(200);
        assertThat(call("GET","/api/admin/statistiques",global,null).statusCode()).isEqualTo(200);
        assertThat(pay("/api/reservations/"+Long.MAX_VALUE+"/paiements",local,UUID.randomUUID(),null).statusCode()).isEqualTo(404);
        assertThat(messages()).isEmpty();
    }
    @Test void recordingOutsideTransactionFailsInsteadOfClaimingSuccess(){
        assertThatThrownBy(()->journal.record(SensitiveOperationJournal.Action.DETTE_CREEE,null,SensitiveOperationJournal.Resource.DETTE,1L))
                .isInstanceOf(IllegalStateException.class);
        assertThat(messages()).isEmpty();
    }
}
