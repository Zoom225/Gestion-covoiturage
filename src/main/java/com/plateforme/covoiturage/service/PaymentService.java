package com.plateforme.covoiturage.service;

import com.plateforme.covoiturage.dto.request.ReferencePageRequest;
import com.plateforme.covoiturage.dto.response.DetteResponse;
import com.plateforme.covoiturage.dto.response.PaymentResponse;
import com.plateforme.covoiturage.dto.response.ReferencePageResponse;
import com.plateforme.covoiturage.entity.Reservation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.Authentication;

/** Contrat applicatif ; implementation dans service.impl. */
public interface PaymentService {
    void refundSeat(Authentication actor,Reservation booking,Instant cancelledAt);

    PaymentResponse paySeat(Authentication actor,long reservationId,UUID key);

    PaymentResponse payDebt(Authentication actor,long debtId,UUID key,BigDecimal amount);

    ReferencePageResponse<PaymentResponse> history(Authentication actor,ReferencePageRequest page);

    ReferencePageResponse<DetteResponse> mine(Authentication actor,ReferencePageRequest page);
}
