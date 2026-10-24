package com.plateforme.covoiturage.service;

import com.plateforme.covoiturage.dto.request.CompteSearchRequest;
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
