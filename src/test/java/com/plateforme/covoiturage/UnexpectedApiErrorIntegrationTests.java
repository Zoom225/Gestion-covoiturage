package com.plateforme.covoiturage;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.plateforme.covoiturage.enums.*;
import com.plateforme.covoiturage.exception.*;
import com.plateforme.covoiturage.payment.PaymentGateway;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UnexpectedApiErrorIntegrationTests extends ReportingIntegrationSupport {
    static final String PRIVATE_DETAIL="detail-technique-fictif-a-ne-pas-divulguer";
    @MockitoBean PaymentGateway gateway;
    final Logger logger=(Logger)LoggerFactory.getLogger(GlobalExceptionHandler.class);
    final Logger rootLogger=(Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    ListAppender<ILoggingEvent> appender;
    ListAppender<ILoggingEvent> applicationLogs;
    @BeforeEach void captureErrors(){
        reset(gateway);when(gateway.validate(any(),any())).thenReturn(PaymentGateway.Mode.SIMULATION);
        appender=new ListAppender<>();appender.start();logger.addAppender(appender);
        applicationLogs=new ListAppender<>();applicationLogs.start();rootLogger.addAppender(applicationLogs);
    }
    @AfterEach void detachErrors(){
        logger.detachAppender(appender);appender.stop();
        rootLogger.detachAppender(applicationLogs);applicationLogs.stop();
    }
    List<String> messages(){return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();}
    String seat(long booking){return "/api/reservations/"+booking+"/paiements";}
    void safeFailure(HttpResponse<String> response,int status){
        assertThat(response.statusCode()).isEqualTo(status);
        var error=read(response);
        assertThat(error.path("status").asInt()).isEqualTo(status);
        assertThat(error.path("code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(error.path("message").asText()).isEqualTo("Erreur interne");
        assertThat(error.path("timestamp").asText()).isNotBlank();
        assertThat(error.path("validationErrors").isArray()).isTrue();
        assertThat(error.path("validationErrors").size()).isZero();
        assertThat(response.body()).doesNotContain(PRIVATE_DETAIL,"IllegalStateException","ErrorResponseException","trace","Bearer");
        String reference=response.headers().firstValue("X-Error-Id").orElseThrow();
        assertThatCode(()->UUID.fromString(reference)).doesNotThrowAnyException();
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(messages()).hasSize(1);
        assertThat(messages().getFirst()).contains(reference).doesNotContain(PRIVATE_DETAIL,hash,"Bearer");
        assertThat(appender.list.getFirst().getThrowableProxy()).isNull();
        assertThat(applicationLogs.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
                .allMatch(message->!message.contains(PRIVATE_DETAIL)&&!message.contains(hash));
    }
    @Test void unexpectedPaymentFailureReturnsUniformSafe500AndRollsBack()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);long booking=booking(trip,local);
        when(gateway.validate(any(),any())).thenThrow(new IllegalStateException(PRIVATE_DETAIL,new RuntimeException(PRIVATE_DETAIL)));
        var response=pay(seat(booking)+"?preuve="+PRIVATE_DETAIL,local,UUID.randomUUID(),null);
        assertThat(seatPayments.count()).isZero();
        assertThat(reservations.findById(booking).orElseThrow().getStatut()).isEqualTo(StatutReservation.EN_ATTENTE);
        verify(gateway).validate(any(),any());
        safeFailure(response,500);
        assertThat(read(response).path("path").asText()).isEqualTo(seat(booking));
    }
    @Test void frameworkServerErrorKeepsItsStatusButNotItsReason()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);
        var failure=new org.springframework.web.ErrorResponseException(HttpStatus.BAD_GATEWAY,
                org.springframework.http.ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,PRIVATE_DETAIL),
                new RuntimeException(PRIVATE_DETAIL));
        failure.getHeaders().set("Authorization",PRIVATE_DETAIL);
        failure.getHeaders().add("Set-Cookie","fictif="+PRIVATE_DETAIL);
        when(gateway.validate(any(),any())).thenThrow(failure);
        var response=pay(seat(booking(trip,local)),local,UUID.randomUUID(),null);
        safeFailure(response,502);
        assertThat(response.headers().firstValue("Authorization")).isEmpty();
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(seatPayments.count()).isZero();
    }
    @Test void knownBusinessConflictStillUses409AndItsExistingCode()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);
        when(gateway.validate(any(),any())).thenThrow(new BusinessRuleException("PAYMENT_REFUSED"));
        var response=pay(seat(booking(trip,local)),local,UUID.randomUUID(),null);
        assertThat(response.statusCode()).isEqualTo(409);assertThat(read(response).path("code").asText()).isEqualTo("PAYMENT_REFUSED");
        assertThat(response.headers().firstValue("X-Error-Id")).isEmpty();assertThat(messages()).isEmpty();
    }
    @Test void knownProviderUnavailabilityStillUses503()throws Exception{
        long trip=create(local,TypeTrajet.PUBLIC);
        when(gateway.validate(any(),any())).thenThrow(new PaymentUnavailableException());
        var response=pay(seat(booking(trip,local)),local,UUID.randomUUID(),null);
        assertThat(response.statusCode()).isEqualTo(503);assertThat(read(response).path("code").asText()).isEqualTo("PAYMENT_UNAVAILABLE");
        assertThat(messages()).isEmpty();
    }
    @Test void securityFailuresNeverReachTheUnexpectedErrorHandler()throws Exception{
        assertThat(call("POST",seat(Long.MAX_VALUE),null,null).statusCode()).isEqualTo(401);
        assertThat(call("POST",seat(Long.MAX_VALUE),global,null).statusCode()).isEqualTo(403);
        verifyNoInteractions(gateway);assertThat(messages()).isEmpty();
    }
    @Test void unsupportedMethodRemains405WithAllowHeaderAndUniformError()throws Exception{
        var response=call("GET",seat(Long.MAX_VALUE),local,null);
        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.headers().firstValue("Allow").orElse("")).contains("POST");
        assertThat(read(response).path("code").asText()).isEqualTo("HTTP_ERROR");
        assertThat(response.headers().firstValue("X-Error-Id")).isEmpty();assertThat(messages()).isEmpty();
    }
    @Test void unsupportedMediaTypeRemains415WithoutLeakingDetails()throws Exception{
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/dettes/"+Long.MAX_VALUE+"/paiements"))
                .header("Authorization","Bearer "+new com.plateforme.covoiturage.service.TokenService(encoder,jwtProperties,java.time.Clock.systemUTC()).issue(local.getId()).accessToken())
                .header("Idempotency-Key",UUID.randomUUID().toString()).header("Content-Type","text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(PRIVATE_DETAIL)).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(415);assertThat(read(response).path("code").asText()).isEqualTo("HTTP_ERROR");
        assertThat(response.body()).doesNotContain(PRIVATE_DETAIL);assertThat(messages()).isEmpty();
    }
    @Test void malformedJsonRetainsExisting400Contract()throws Exception{
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/dettes/"+Long.MAX_VALUE+"/paiements"))
                .header("Authorization","Bearer "+new com.plateforme.covoiturage.service.TokenService(encoder,jwtProperties,java.time.Clock.systemUTC()).issue(local.getId()).accessToken())
                .header("Idempotency-Key",UUID.randomUUID().toString()).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{")).build();
        var response=http.send(request,HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(400);assertThat(read(response).path("code").asText()).isEqualTo("INVALID_REQUEST");
        assertThat(messages()).isEmpty();
    }
}
