package com.plateforme.covoiturage.service.impl;

import com.plateforme.covoiturage.dto.response.TokenResponse;
import com.plateforme.covoiturage.security.JwtProperties;
import com.plateforme.covoiturage.service.TokenService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {
    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    /**
     * Émet pour le compte authentifié un JWT à durée, émetteur et audience configurés, sans y figer ses droits métier.
     */
    @Override
    public TokenResponse issue(Long compteId) {
        var now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer(properties.issuer())
                .audience(List.of(properties.audience())).subject(compteId.toString())
                .issuedAt(now).expiresAt(now.plus(properties.ttl())).id(UUID.randomUUID().toString()).build();
        var token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
        return new TokenResponse(token.getTokenValue(), "Bearer", properties.ttl().toSeconds());
    }
}
