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
    private final InvitationService invitations;
    private final com.plateforme.covoiturage.repository.InvitationOutboxRepository outbox;
    private final SensitiveOperationJournal journal;

    public AuthService(CompteRepository comptes, PasswordEncoder passwords, TokenService tokens, InvitationService invitations, com.plateforme.covoiturage.repository.InvitationOutboxRepository outbox, SensitiveOperationJournal journal) {
        this.comptes = comptes;
        this.passwords = passwords;
        this.tokens = tokens;
        this.invitations = invitations;
        this.outbox = outbox;
        this.journal = journal;
        this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    /**
     * Authentifie un compte à accès effectif avec BCrypt, sans distinguer publiquement un identifiant inconnu, un mauvais mot de passe ou un compte désactivé.
     */
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

    /**
     * Relit le compte identifié par le sujet JWT et refuse les comptes ou clients sans accès effectif avant de restituer leurs droits actuels.
     */
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


    /**
     * Consomme la preuve et initialise atomiquement le mot de passe conforme à DEC-COMPTES-006, sans réactiver un compte ou client désactivé.
     */
    @Transactional
    public void accepterInvitation(String jeton, String password) {
        if (password == null || password.isBlank() || password.codePointCount(0, password.length()) < 15
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new com.plateforme.covoiturage.exception.InvalidAccountRequestException();
        }
        Long compteId = invitations.consommer(jeton);
        // La consommation conserve le verrou compte jusqu'au commit englobant.
        boolean compteActif = comptes.activiteCompteVerrouillee(compteId).orElse(false);
        boolean clientActif = comptes.activiteClientVerrouillee(compteId).orElse(true);
        if (!compteActif || !clientActif
                || comptes.initialiserSiActif(compteId, passwords.encode(password)) != 1) {
            throw new com.plateforme.covoiturage.exception.InvitationInvalideException();
        }
        outbox.cancelForAccount(compteId);
        journal.recordByAccount(SensitiveOperationJournal.Action.COMPTE_ACTIVE,compteId,
                SensitiveOperationJournal.Resource.COMPTE,compteId,null);
    }

    private static BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("Authentification requise ou invalide");
    }
}
