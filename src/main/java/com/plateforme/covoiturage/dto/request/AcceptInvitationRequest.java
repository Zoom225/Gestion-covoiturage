package com.plateforme.covoiturage.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record AcceptInvitationRequest(
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}")
        @Schema(accessMode=Schema.AccessMode.WRITE_ONLY) String jeton,
        @NotBlank @Size(max=72)
        @Schema(format="password",accessMode=Schema.AccessMode.WRITE_ONLY,
                description="Au moins 15 caractères Unicode et au plus 72 octets UTF-8") String password) {
    @JsonAnySetter public void refuseUnknown(String name, Object value) { throw new IllegalArgumentException("Champ non autorisé"); }
    @Override public String toString() { return "AcceptInvitationRequest[REDACTED]"; }
}
