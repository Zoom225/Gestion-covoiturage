package com.plateforme.covoiturage.dto.response;

import java.time.Instant;

/** PENDING signifie en file, pas livré au destinataire. */
public record InvitationStatusResponse(String statut, Instant expireA) {}
