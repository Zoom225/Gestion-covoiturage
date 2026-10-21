package com.plateforme.covoiturage.security;

import com.plateforme.covoiturage.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.BearerTokenErrors;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DatabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final AuthService auth;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            var principal = auth.resolvePrincipal(jwt.getSubject());
            var authority = new SimpleGrantedAuthority("ROLE_" + principal.profile().role().name());
            return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of(authority));
        } catch (AuthenticationException ex) {
            throw new OAuth2AuthenticationException(BearerTokenErrors.invalidToken("Compte indisponible"));
        }
    }
}
