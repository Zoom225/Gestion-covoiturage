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
 /**
  * N'autorise la simulation de paiement que si elle est explicitement activée en dev sans profil prod ; ne simule aucune intégration bancaire réelle.
  */
 @Override public Mode validate(UUID operation,BigDecimal amount){
  if(!environment.acceptsProfiles(Profiles.of("dev & !prod"))
    || !environment.getProperty("driveconnect.payments.simulation-enabled",Boolean.class,false))
   throw new PaymentUnavailableException();
  return Mode.SIMULATION;
 }
 /** Simulation uniquement, avec les mêmes conditions d'activation que le paiement. */
 @Override public void refund(UUID paymentOperation,BigDecimal amount){
  validate(paymentOperation,amount);
 }
}
