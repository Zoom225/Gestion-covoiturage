package com.plateforme.covoiturage;
import com.plateforme.covoiturage.payment.*;
import com.plateforme.covoiturage.exception.PaymentUnavailableException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class SimulationGatewayTests {
 @ParameterizedTest @CsvSource({"dev,true,true","prod,true,false","dev+prod,true,false","dev,false,false","test,true,false"})
 void simulationRequiresExplicitDevAndNeverProd(String profiles,boolean enabled,boolean allowed){
  var environment=new MockEnvironment().withProperty("driveconnect.payments.simulation-enabled",Boolean.toString(enabled));
  environment.setActiveProfiles(profiles.split("\\+"));
  var gateway=new ConfiguredPaymentGateway(environment);
  if(allowed)assertThat(gateway.validate(UUID.randomUUID(),new BigDecimal("20.00"))).isEqualTo(PaymentGateway.Mode.SIMULATION);
  else assertThatThrownBy(()->gateway.validate(UUID.randomUUID(),new BigDecimal("20.00"))).isInstanceOf(PaymentUnavailableException.class);
 }
}
