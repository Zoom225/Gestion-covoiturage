package com.plateforme.covoiturage.scheduling;
import com.plateforme.covoiturage.service.J1ProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
@Configuration(proxyBeanMethods=false) @EnableScheduling @RequiredArgsConstructor
@ConditionalOnProperty(name="driveconnect.scheduling.j1.enabled",havingValue="true",matchIfMissing=true)
public class J1Scheduler {
 private final J1ProcessingService service;
 @Scheduled(cron="0 15 0 * * *",zone="Europe/Brussels")
 public void process(){service.catchUp();}
}
