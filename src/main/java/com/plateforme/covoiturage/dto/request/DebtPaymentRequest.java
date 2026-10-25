package com.plateforme.covoiturage.dto.request;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record DebtPaymentRequest(@NotNull @DecimalMin(value="0",inclusive=false) @DecimalMax("80.00")
 @Digits(integer=2,fraction=2) BigDecimal montant) {}
