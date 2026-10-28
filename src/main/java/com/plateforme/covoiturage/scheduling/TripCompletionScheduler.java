package com.plateforme.covoiturage.scheduling;

import com.plateforme.covoiturage.service.TrajetService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods=false)
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name="driveconnect.scheduling.completion.enabled",havingValue="true",matchIfMissing=true)
public class TripCompletionScheduler {
 private final TrajetService service;

 @Scheduled(fixedDelayString="${driveconnect.scheduling.completion.delay-ms:60000}")
 public void process(){service.terminerTrajetsArrives();}
}
