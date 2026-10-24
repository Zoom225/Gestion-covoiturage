package com.plateforme.covoiturage;

import com.plateforme.covoiturage.delivery.*;
import com.plateforme.covoiturage.exception.DeliveryUnavailableException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.*;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class SmtpInvitationTransportTests {
    static class Sender extends JavaMailSenderImpl {
        MimeMessage sent;
        boolean fail;
        String unsafe;
        @Override public void send(MimeMessage message) {
            if(fail) throw new MailSendException("unsafe:"+unsafe);
            sent=message;
        }
    }
    private static final String TOKEN=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

    private SmtpInvitationTransport transport(Sender sender,String url,String profile){
        var factory=new DefaultListableBeanFactory();
        if(sender!=null)factory.registerSingleton("sender",sender);
        var environment=new MockEnvironment();
        environment.setActiveProfiles(profile);
        return new SmtpInvitationTransport(factory.getBeanProvider(JavaMailSender.class),
                new DeliveryProperties(null,null,url,"noreply@driveconnect.test",Duration.ofMinutes(1)),environment);
    }
    private Sender sender(){
        var sender=new Sender();
        sender.getJavaMailProperties().setProperty("mail.smtp.starttls.required","true");
        sender.getJavaMailProperties().setProperty("mail.debug","true");
        return sender;
    }

    @Test void mimeMessageUsesAFragmentAndDebuggingIsForcedOff() throws Exception{
        var sender=sender();
        UUID id=UUID.randomUUID();
        transport(sender,"https://frontend.example.test/activation","prod").send(id,"fiction@delivery.test",TOKEN);
        assertThat(sender.sent!=null).isTrue();
        assertThat(sender.sent.getContent().toString().contains("#invitation="+TOKEN)).isTrue();
        assertThat(sender.sent.getHeader("X-DriveConnect-Delivery-Id",null)).isEqualTo(id.toString());
        assertThat(sender.getSession().getDebug()).isFalse();
        assertThat(sender.getJavaMailProperties().getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
    }

    @ParameterizedTest
    @ValueSource(strings={"http://remote.example.test/activation","https://user@frontend.example.test/path",
            "https://frontend.example.test/path?redirect=unsafe","https://frontend.example.test/path#old","not-a-uri"})
    void unsafeAcceptanceUrlsFailBeforeSending(String url){
        var sender=sender();
        assertThatThrownBy(()->transport(sender,url,"prod").send(UUID.randomUUID(),"fiction@delivery.test",TOKEN))
                .isInstanceOf(DeliveryUnavailableException.class).hasMessage("Livraison d'invitation indisponible");
        assertThat(sender.sent==null).isTrue();
    }

    @Test void localhostHttpIsLimitedToDevelopment(){
        var sender=sender();
        transport(sender,"http://localhost:4200/activation","dev").send(UUID.randomUUID(),"fiction@delivery.test",TOKEN);
        assertThat(sender.sent!=null).isTrue();
        assertThatThrownBy(()->transport(sender,"http://localhost:4200/activation","prod")
                .send(UUID.randomUUID(),"fiction@delivery.test",TOKEN)).isInstanceOf(DeliveryUnavailableException.class);
    }

    @Test void smtpTlsIsRequired(){
        var sender=sender();
        sender.getJavaMailProperties().setProperty("mail.smtp.starttls.required","false");
        assertThatThrownBy(()->transport(sender,"https://frontend.example.test/activation","prod")
                .send(UUID.randomUUID(),"fiction@delivery.test",TOKEN)).isInstanceOf(DeliveryUnavailableException.class);
        assertThat(sender.sent==null).isTrue();
    }

    @Test void providerFailuresNeverExposeTheirMessageOrCause(){
        var sender=sender();sender.fail=true;sender.unsafe=TOKEN;
        try{
            transport(sender,"https://frontend.example.test/activation","prod").send(UUID.randomUUID(),"fiction@delivery.test",TOKEN);
            throw new AssertionError("expected delivery failure");
        }catch(DeliveryUnavailableException expected){
            assertThat(expected.getCause()==null).isTrue();
            assertThat(expected.getMessage().contains(TOKEN)).isFalse();
        }
    }

    @Test void missingSmtpIsNotAnApplicationStartupDependency(){
        assertThatThrownBy(()->transport(null,"https://frontend.example.test/activation","prod")
                .send(UUID.randomUUID(),"fiction@delivery.test",TOKEN)).isInstanceOf(DeliveryUnavailableException.class);
    }

    @Test void addressHeaderInjectionIsRejected(){
        var sender=sender();
        assertThatThrownBy(()->transport(sender,"https://frontend.example.test/activation","prod")
                .send(UUID.randomUUID(),"fiction@delivery.test\r\nBcc:other@delivery.test",TOKEN))
                .isInstanceOf(DeliveryUnavailableException.class);
        assertThat(sender.sent==null).isTrue();
    }
}
