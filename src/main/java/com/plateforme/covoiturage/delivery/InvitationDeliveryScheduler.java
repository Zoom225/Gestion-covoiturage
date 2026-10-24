package com.plateforme.covoiturage.delivery;

import com.plateforme.covoiturage.service.InvitationDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "driveconnect.delivery.enabled", havingValue = "true")
@RequiredArgsConstructor
public class InvitationDeliveryScheduler {
    private final InvitationDeliveryService delivery;
    @Scheduled(fixedDelayString = "${driveconnect.delivery.poll-delay:10000}")
    void dispatch() {
        try { delivery.dispatchNext(); }
        catch (RuntimeException ex) {
            // Reprise au prochain passage ; ne jamais journaliser des causes de transport.
        }
    }
}
