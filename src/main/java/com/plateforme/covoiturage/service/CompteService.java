package com.plateforme.covoiturage.service;

import com.plateforme.covoiturage.dto.request.*;
import com.plateforme.covoiturage.dto.response.*;
import com.plateforme.covoiturage.entity.Client;
import com.plateforme.covoiturage.entity.Zone;
import com.plateforme.covoiturage.enums.CategorieClient;
import com.plateforme.covoiturage.enums.PerimetreAdmin;
import com.plateforme.covoiturage.exception.InvalidAccountRequestException;
import com.plateforme.covoiturage.exception.InvitationConflictException;
import com.plateforme.covoiturage.repository.ClientRepository;
import com.plateforme.covoiturage.repository.ZoneRepository;
import com.plateforme.covoiturage.repository.InvitationOutboxRepository;
import jakarta.validation.Validator;
import com.plateforme.covoiturage.dto.response.CompteAdministrationResponse;
import com.plateforme.covoiturage.dto.response.ComptePageResponse;
import com.plateforme.covoiturage.entity.Compte;
import com.plateforme.covoiturage.enums.RoleTechnique;
import com.plateforme.covoiturage.exception.ResourceNotFoundException;
import com.plateforme.covoiturage.mapper.CompteMapper;
import com.plateforme.covoiturage.repository.CompteRepository;
import com.plateforme.covoiturage.security.AdminAuthorization;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompteService {
    private final CompteRepository comptes;
    private final AdminAuthorization authorization;
    private final CompteMapper mapper;
    private final ClientRepository clients;
    private final ZoneRepository zones;
    private final ClientNumberGenerator numbers;
    private final InvitationService invitations;
    private final InvitationDeliveryService delivery;
    private final InvitationOutboxRepository outbox;
    private final Validator validator;
    private final SensitiveOperationJournal journal;

    @Transactional
    public CompteInvitationResponse createClient(Authentication actor, CreateClientRequest request) {
        validate(request);
        authorization.requireClientCreation(actor, request.categorie(), request.zoneClientId());
        if (request.categorie() == CategorieClient.LOCAL && request.zoneClientId() == null) {
            throw new InvalidAccountRequestException();
        }
        Zone zone = zone(request.zoneClientId());
        String number = numbers.reserve(request.categorie());
        var client = clients.saveAndFlush(new Client(number, request.categorie(), true, zone));
        var compte = Compte.invite(number, true, RoleTechnique.CLIENT, client, null, null);
        compte.definirContactInvitation(canonicalEmail(request.email()));
        var result=createAndQueue(compte);
        journal.record(SensitiveOperationJournal.Action.COMPTE_CREE,actor,SensitiveOperationJournal.Resource.COMPTE,compte.getId());
        return result;
    }

    @Transactional
    public CompteInvitationResponse createAdministrateur(Authentication actor, CreateAdministrateurRequest request) {
        authorization.requireGlobal(actor);
        validate(request);
        comptes.verrouillerAdministrationGlobale();
        var result=createAdmin(request);
        journal.record(SensitiveOperationJournal.Action.COMPTE_CREE,actor,SensitiveOperationJournal.Resource.COMPTE,result.compte().compteId());
        return result;
    }

    /** Appelé uniquement par le point d'entrée opérateur, jamais par un contrôleur. */
    @Transactional
    public void bootstrapGlobal(String email) {
        var request = new CreateAdministrateurRequest(email, PerimetreAdmin.GLOBAL, null);
        validate(request);
        comptes.verrouillerAdministrationGlobale();
        if (comptes.existsByRoleAndPerimetreAdmin(RoleTechnique.ADMIN, PerimetreAdmin.GLOBAL)) {
            throw new InvitationConflictException();
        }
        var result=createAdmin(request);
        journal.record(SensitiveOperationJournal.Action.COMPTE_CREE,null,SensitiveOperationJournal.Resource.COMPTE,result.compte().compteId());
    }

    @Transactional
    public InvitationStatusResponse renewInvitation(Authentication actor, long id) {
        // Scope SQL avant toute divulgation ; invisible et absent ont le même 404.
        var spec = withinScope(actor).and((root, query, cb) -> cb.equal(root.get("id"), id));
        var compte = comptes.findOne(spec).orElseThrow(ResourceNotFoundException::new);
        authorization.requireInvitationManagement(actor, compte);
        if (!"INVITE".equals(comptes.verrouillerEtatPourInvitation(id).orElse(""))
                || !comptes.activiteCompteVerrouillee(id).orElse(false)
                || !comptes.activiteClientVerrouillee(id).orElse(true)
                || compte.getInvitationEmail() == null) {
            throw new InvitationConflictException();
        }
        outbox.cancelForAccount(id);
        var issued = invitations.renouveler(id);
        delivery.enqueue(compte.getInvitationEmail(), issued);
        journal.record(SensitiveOperationJournal.Action.INVITATION_RENOUVELEE,actor,SensitiveOperationJournal.Resource.COMPTE,id);
        return new InvitationStatusResponse("PENDING", issued.expireA());
    }

    private CompteInvitationResponse createAdmin(CreateAdministrateurRequest request) {
        if ((request.perimetreAdmin() == PerimetreAdmin.LOCAL) != (request.zoneAdminId() != null)) {
            throw new InvalidAccountRequestException();
        }
        String email = canonicalEmail(request.email());
        var compte = Compte.invite(email, true, RoleTechnique.ADMIN, null,
                request.perimetreAdmin(), zone(request.zoneAdminId()));
        compte.definirContactInvitation(email);
        return createAndQueue(compte);
    }

    private CompteInvitationResponse createAndQueue(Compte compte) {
        comptes.saveAndFlush(compte);
        var issued = invitations.emettre(compte.getId());
        delivery.enqueue(compte.getInvitationEmail(), issued);
        return new CompteInvitationResponse(mapper.toResponse(compte), issued.expireA());
    }

    private Zone zone(Long id) {
        return id == null ? null : zones.findById(id).orElseThrow(ResourceNotFoundException::new);
    }

    private String canonicalEmail(String email) {
        return email.toLowerCase(java.util.Locale.ROOT);
    }

    private void validate(Object request) {
        if (request == null || !validator.validate(request).isEmpty()) throw new InvalidAccountRequestException();
    }


    public ComptePageResponse search(Authentication actor, CompteSearchRequest search) {
        var specification = withinScope(actor).and(filters(search));
        var page = comptes.findAll(specification,
                PageRequest.of(search.page(), search.size(), Sort.by("id").ascending()));
        return new ComptePageResponse(page.getContent().stream().map(mapper::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public CompteAdministrationResponse findById(Authentication actor, long id) {
        var specification = withinScope(actor)
                .and((root, query, builder) -> builder.equal(root.get("id"), id));
        // Une seule recherche déjà filtrée : aucune vérification d'existence globale.
        return mapper.toResponse(comptes.findOne(specification).orElseThrow(ResourceNotFoundException::new));
    }

    private Specification<Compte> withinScope(Authentication actor) {
        Long zoneId = authorization.requireAccountReadZone(actor);
        return (root, query, builder) -> {
            if (zoneId == null) {
                return builder.conjunction(); // GLOBAL, confirmé par la politique.
            }
            var client = root.join("client", JoinType.LEFT);
            var zone = client.join("zone", JoinType.LEFT);
            return builder.and(builder.equal(root.get("role"), RoleTechnique.CLIENT),
                    builder.equal(zone.get("id"), zoneId));
        };
    }

    private Specification<Compte> filters(CompteSearchRequest search) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (search.identifiant() != null) {
                // Même normalisation PostgreSQL que login et l'index unique V4.
                predicates.add(builder.equal(builder.lower(root.get("identifiant")),
                        builder.lower(builder.literal(search.identifiant()))));
            }
            if (search.role() != null) {
                predicates.add(builder.equal(root.get("role"), search.role()));
            }
            if (search.compteActif() != null) {
                predicates.add(builder.equal(root.get("actif"), search.compteActif()));
            }
            if (search.categorie() != null || search.clientActif() != null || search.zoneId() != null) {
                var client = root.join("client", JoinType.LEFT);
                if (search.categorie() != null) {
                    predicates.add(builder.equal(client.get("categorie"), search.categorie()));
                }
                if (search.clientActif() != null) {
                    predicates.add(builder.equal(client.get("actif"), search.clientActif()));
                }
                if (search.zoneId() != null) {
                    var clientZone = client.join("zone", JoinType.LEFT);
                    var adminZone = root.join("zoneAdmin", JoinType.LEFT);
                    predicates.add(builder.or(
                            builder.and(builder.equal(root.get("role"), RoleTechnique.CLIENT),
                                    builder.equal(clientZone.get("id"), search.zoneId())),
                            builder.and(builder.equal(root.get("role"), RoleTechnique.ADMIN),
                                    builder.equal(adminZone.get("id"), search.zoneId()))));
                }
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
