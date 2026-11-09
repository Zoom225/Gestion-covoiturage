package com.plateforme.covoiturage.service.impl;

import com.plateforme.covoiturage.entity.Compte;
import com.plateforme.covoiturage.entity.Invitation;
import com.plateforme.covoiturage.entity.enums.EtatIdentifiants;
import com.plateforme.covoiturage.exception.InvitationConflictException;
import com.plateforme.covoiturage.exception.InvitationInvalideException;
import com.plateforme.covoiturage.exception.ResourceNotFoundException;
import com.plateforme.covoiturage.repository.CompteRepository;
import com.plateforme.covoiturage.repository.InvitationRepository;
import com.plateforme.covoiturage.security.InvitationProperties;
import com.plateforme.covoiturage.service.AuthService;
import com.plateforme.covoiturage.service.InvitationService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@EnableConfigurationProperties(InvitationProperties.class)
public class InvitationServiceImpl implements InvitationService {
    private final CompteRepository comptes;
    private final InvitationRepository invitations;
    private final InvitationProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public InvitationServiceImpl(CompteRepository comptes, InvitationRepository invitations,
                             InvitationProperties properties, Clock clock) {
        this.comptes = comptes;
        this.invitations = invitations;
        this.properties = properties;
        this.clock = clock;
    }

/**
     * Émet une preuve d'invitation pour un compte encore INVITE sans remplacer implicitement une preuve ouverte.
     */
    @Override
    public InvitationEmise emettre(Long compteId) {
        return preparer(compteId, false);
    }

    /**
     * Révoque les preuves ouvertes puis émet une nouvelle invitation dans la même transaction verrouillée.
     */
    @Override
    public InvitationEmise renouveler(Long compteId) {
        return preparer(compteId, true);
    }

    /**
     * Verrouille le compte avant de révoquer ses invitations encore ouvertes sans modifier son état d'accès.
     */
    @Override
    public void revoquer(Long compteId) {
        comptes.verrouillerEtatPourInvitation(compteId).orElseThrow(ResourceNotFoundException::new);
        invitations.revoquerOuvertes(compteId, maintenant());
    }

    /**
     * Consomme une seule preuve valide sous verrou pour un compte INVITE ; l'initialisation du mot de passe reste dans la transaction englobante d'AuthService.
     */
    @Override
    public Long consommer(String jeton) {
        String empreinte = empreinte(jeton);
        Long compteId = invitations.findCompteIdByEmpreinte(empreinte)
                .orElseThrow(InvitationInvalideException::new);
        String etat = comptes.verrouillerEtatPourInvitation(compteId)
                .orElseThrow(InvitationInvalideException::new);
        if (!EtatIdentifiants.INVITE.name().equals(etat)
                || invitations.consommerSiValide(empreinte, maintenant()) != 1) {
            throw new InvitationInvalideException();
        }
        return compteId;
    }

    /**
     * Exige une durée explicite et un compte INVITE verrouillé avant de conserver uniquement l'empreinte d'une nouvelle preuve à expiration déterminée.
     */
    private InvitationEmise preparer(Long compteId, boolean renouvellement) {
        // Hors dev, aucune durée implicite : l'exploitation doit configurer INVITATION_TTL.
        if (properties.ttl() == null) {
            throw new IllegalStateException("Invitation TTL must be configured before issuing invitations");
        }
        String etat = comptes.verrouillerEtatPourInvitation(compteId).orElseThrow(ResourceNotFoundException::new);
        if (!EtatIdentifiants.INVITE.name().equals(etat)) {
            throw new InvitationConflictException();
        }
        Instant maintenant = maintenant();
        Instant expiration = maintenant.plus(properties.ttl()).truncatedTo(ChronoUnit.MICROS);
        if (!expiration.isAfter(maintenant)) {
            throw new IllegalStateException("Invitation TTL must exceed PostgreSQL timestamp precision");
        }
        if (renouvellement) {
            // UPDATE exécuté avant l'INSERT : l'index partiel est libéré dans la même transaction.
            invitations.revoquerOuvertes(compteId, maintenant);
        } else if (invitations.existsByCompteIdAndConsommeeAIsNullAndRevoqueeAIsNull(compteId)) {
            throw new InvitationConflictException();
        }
        Compte compte = comptes.getReferenceById(compteId);
        byte[] octets = new byte[32];
        random.nextBytes(octets);
        String jeton = Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
        var invitation = invitations.saveAndFlush(new Invitation(compte, sha256(octets), maintenant, expiration));
        return new InvitationEmise(jeton, expiration, invitation.getId());
    }

    private Instant maintenant() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Refuse un jeton absent, mal formé ou non canonique avant de calculer son empreinte, sans conserver le secret en clair.
     */
    private String empreinte(String jeton) {
        if (jeton == null || !jeton.matches("[A-Za-z0-9_-]{43}")) throw new InvitationInvalideException();
        byte[] octets;
        try {
            octets = Base64.getUrlDecoder().decode(jeton);
        } catch (IllegalArgumentException ex) {
            throw new InvitationInvalideException();
        }
        if (octets.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(octets).equals(jeton)) {
            throw new InvitationInvalideException();
        }
        return sha256(octets);
    }

    private String sha256(byte[] octets) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(octets));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}
