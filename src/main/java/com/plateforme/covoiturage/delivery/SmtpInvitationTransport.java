package com.plateforme.covoiturage.delivery;

import com.plateforme.covoiturage.exception.DeliveryUnavailableException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.UUID;

@Component
public class SmtpInvitationTransport implements InvitationMailTransport {
    private final ObjectProvider<JavaMailSender> senders;
    private final DeliveryProperties properties;
    private final Environment environment;

    public SmtpInvitationTransport(ObjectProvider<JavaMailSender> senders, DeliveryProperties properties,
                                   Environment environment) {
        this.senders = senders;
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void send(UUID messageId, String recipient, String token) {
        try {
            if (!(senders.getIfAvailable() instanceof JavaMailSenderImpl sender)
                    || properties.from() == null || properties.from().isBlank()) throw new DeliveryUnavailableException();
            URI base = URI.create(properties.acceptanceUrl());
            boolean localDev = environment.acceptsProfiles(Profiles.of("dev & !prod"))
                    && "http".equals(base.getScheme())
                    && ("localhost".equals(base.getHost()) || "127.0.0.1".equals(base.getHost()));
            if ((!localDev && !"https".equals(base.getScheme())) || base.getHost() == null
                    || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                    || token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new DeliveryUnavailableException();
            var mail = sender.getJavaMailProperties();
            if (!Boolean.parseBoolean(mail.getProperty("mail.smtp.starttls.required"))
                    && !Boolean.parseBoolean(mail.getProperty("mail.smtp.ssl.enable"))) throw new DeliveryUnavailableException();
            mail.setProperty("mail.debug", "false");
            mail.setProperty("mail.smtp.ssl.checkserveridentity", "true");
            mail.setProperty("mail.smtp.connectiontimeout", "5000");
            mail.setProperty("mail.smtp.timeout", "5000");
            mail.setProperty("mail.smtp.writetimeout", "5000");
            sender.getSession().setDebug(false);
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(new InternetAddress(properties.from(), true));
            helper.setTo(new InternetAddress(recipient, true));
            helper.setSubject("DriveConnect — initialisation de votre compte");
            helper.setText("Choisissez personnellement votre mot de passe via ce lien temporaire :\n"
                    + base.toASCIIString() + "#invitation=" + token
                    + "\nCe lien est à usage unique. Aucun mot de passe n'est envoyé par e-mail.");
            message.setHeader("X-DriveConnect-Delivery-Id", messageId.toString());
            sender.send(message);
        } catch (Exception ex) {
            // Ni le message du fournisseur SMTP ni sa cause ne sortent de cette frontière.
            throw new DeliveryUnavailableException();
        }
    }
}
