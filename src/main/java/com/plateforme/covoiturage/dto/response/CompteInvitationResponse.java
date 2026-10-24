package com.plateforme.covoiturage.dto.response;

import java.time.Instant;

public record CompteInvitationResponse(CompteAdministrationResponse compte, Instant invitationExpireA) {}
