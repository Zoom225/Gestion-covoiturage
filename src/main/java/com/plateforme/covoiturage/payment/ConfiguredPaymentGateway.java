package com.plateforme.covoiturage.payment;
import com.plateforme.covoiturage.exception.PaymentUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.UUID;
@Component @RequiredArgsConstructor
public class ConfiguredPaymentGateway implements PaymentGateway {
 private final Environment environment;
 @Override public Mode validate(UUID operation,BigDecimal amount){
  if(!environment.acceptsProfiles(Profiles.of("dev & !prod"))
    || !environment.getProperty("driveconnect.payments.simulation-enabled",Boolean.class,false))
   throw new PaymentUnavailableException();
  return Mode.SIMULATION;
 }
}
