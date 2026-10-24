package com.plateforme.covoiturage.service;

import com.plateforme.covoiturage.dto.request.LoginRequest;
import com.plateforme.covoiturage.dto.response.ProfileResponse;
import com.plateforme.covoiturage.dto.response.TokenResponse;
import com.plateforme.covoiturage.entity.Compte;
import com.plateforme.covoiturage.repository.CompteRepository;
import com.plateforme.covoiturage.security.ComptePrincipal;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
@Transactional(readOnly = true)
public class AuthService {
    private final CompteRepository comptes;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final String dummyHash;

    public AuthService(CompteRepository comptes, PasswordEncoder passwords, TokenService tokens) {
        this.comptes = comptes;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    public TokenResponse login(LoginRequest request) {
        // BCrypt limite les entrées à 72 octets, y compris pour les caractères Unicode.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalidCredentials();
        }
        var compte = comptes.findByIdentifiantIgnoringCase(request.identifiant()).orElse(null);
        String hash = compte == null ? null : compte.getPasswordHash();
        boolean matches = passwords.matches(request.password(), hash == null ? dummyHash : hash);
        if (!matches || compte == null || !compte.isAccesEffectif()) {
            throw invalidCredentials();
        }
        return tokens.issue(compte.getId());
    }

    public ComptePrincipal resolvePrincipal(String subject) {
        long id;
        try {
            id = Long.parseLong(subject);
        } catch (NumberFormatException ex) {
            throw invalidCredentials();
        }
        var compte = comptes.findById(id).filter(Compte::isAccesEffectif).orElseThrow(AuthService::invalidCredentials);
        var client = compte.getClient();
        return new ComptePrincipal(new ProfileResponse(compte.getId(), compte.getIdentifiant(), compte.getRole(),
                client == null ? null : client.getId(), client == null ? null : client.getCategorie(),
                client == null || client.getZone() == null ? null : client.getZone().getId(),
                compte.getPerimetreAdmin(), compte.getZoneAdmin() == null ? null : compte.getZoneAdmin().getId()));
    }

    public ProfileResponse me(ComptePrincipal principal) {
        return principal.profile();
    }

    private static BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("Authentification requise ou invalide");
    }
}
