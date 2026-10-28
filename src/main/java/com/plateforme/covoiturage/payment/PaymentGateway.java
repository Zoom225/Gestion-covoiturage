package com.plateforme.covoiturage.payment;
import java.math.BigDecimal;
import java.util.UUID;
/** Server-owned validation, never an accepted=true value supplied by a browser. */
public interface PaymentGateway {
 enum Mode { SIMULATION }
 Mode validate(UUID operation,BigDecimal amount);
 void refund(UUID paymentOperation,BigDecimal amount);
}
