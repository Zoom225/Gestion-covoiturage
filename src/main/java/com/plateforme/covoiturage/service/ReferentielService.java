package com.plateforme.covoiturage.service;

import com.plateforme.covoiturage.dto.request.ReferencePageRequest;
import com.plateforme.covoiturage.dto.response.*;
import com.plateforme.covoiturage.entity.*;
import com.plateforme.covoiturage.exception.ResourceNotFoundException;
import com.plateforme.covoiturage.mapper.ReferentielMapper;
import com.plateforme.covoiturage.repository.*;
import com.plateforme.covoiturage.security.AdminAuthorization;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultation des référentiels ; aucun droit de mutation n'est déduit du droit de lecture. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class ReferentielService {
    private final ZoneRepository zones;
    private final VehiculeRepository vehicules;
    private final IndisponibiliteRepository indisponibilites;
    private final AdminAuthorization authorization;
    private final ReferentielMapper mapper;
    private final ClientRepository clients;
    private final ZoneAccessRepository grants;

    @Transactional
    public ZoneResponse createZone(Authentication actor,com.plateforme.covoiturage.dto.request.ZoneWriteRequest request){
        authorization.requireGlobal(actor); validateTimezone(request.fuseauHoraire());
        var zone=new Zone(request.nom().strip());
        zone.configure(request.nom().strip(),request.fuseauHoraire(),request.ouverteOccasionnels());
        return mapper.zone(zones.save(zone));
    }
    @Transactional
    public ZoneResponse updateZone(Authentication actor,long id,com.plateforme.covoiturage.dto.request.ZoneWriteRequest request){
        authorization.requireGlobal(actor);validateTimezone(request.fuseauHoraire());
        var zone=zones.lockById(id).orElseThrow(ResourceNotFoundException::new);
        zone.configure(request.nom().strip(),request.fuseauHoraire(),request.ouverteOccasionnels());
        return mapper.zone(zone);
    }
    @Transactional
    public ZoneResponse zoneStatus(Authentication actor,long id,boolean active){
        authorization.requireGlobal(actor);
        var zone=zones.lockById(id).orElseThrow(ResourceNotFoundException::new);
        zone.setActif(active);return mapper.zone(zone);
    }
    @Transactional
    public void vipAccess(Authentication actor,long zoneId,long clientId,boolean allow){
        authorization.requireGlobal(actor);
        zones.lockById(zoneId).orElseThrow(ResourceNotFoundException::new);
        var client=clients.findById(clientId).orElseThrow(ResourceNotFoundException::new);
        if(client.getCategorie()!=com.plateforme.covoiturage.enums.CategorieClient.VIP)
            throw new com.plateforme.covoiturage.exception.BusinessRuleException("VIP_REQUIRED");
        if(allow)grants.grant(clientId,zoneId);else grants.revoke(clientId,zoneId);
    }
    private void validateTimezone(String timezone){
        if(!java.time.ZoneId.getAvailableZoneIds().contains(timezone))
            throw new com.plateforme.covoiturage.exception.InvalidAccountRequestException();
    }

    public ReferencePageResponse<ZoneResponse> zones(Authentication actor,ReferencePageRequest page) {
        Long scope=authorization.requireZoneScope(actor);
        return ReferencePageResponse.from(zones.findAll(zoneScope(scope),pageable(page)).map(mapper::zone));
    }
    public ZoneResponse zone(Authentication actor,long id) {
        Long scope=authorization.requireZoneScope(actor);
        return mapper.zone(zones.findOne(zoneScope(scope).and(id(id))).orElseThrow(ResourceNotFoundException::new));
    }
    public ReferencePageResponse<VehiculeResponse> vehicules(Authentication actor,ReferencePageRequest page,Long zoneId) {
        Long scope=authorization.requireZoneScope(actor);
        var spec=vehiculeScope(scope).and(vehiculeScope(zoneId));
        return ReferencePageResponse.from(vehicules.findAll(spec,pageable(page)).map(mapper::vehicule));
    }
    public VehiculeResponse vehicule(Authentication actor,long id) {
        Long scope=authorization.requireZoneScope(actor);
        return mapper.vehicule(vehicules.findOne(vehiculeScope(scope).and(id(id)))
                .orElseThrow(ResourceNotFoundException::new));
    }
    public ReferencePageResponse<IndisponibiliteResponse> indisponibilites(Authentication actor,ReferencePageRequest page,
                                                                          Long zoneId,Long vehiculeId) {
        Long scope=authorization.requireZoneScope(actor);
        var spec=periodScope(scope).and(periodScope(zoneId));
        if(vehiculeId!=null) {
            spec=spec.and((root,query,cb)->cb.equal(root.join("vehicule",JoinType.LEFT).get("id"),vehiculeId));
        }
        return ReferencePageResponse.from(indisponibilites.findAll(spec,pageable(page)).map(mapper::indisponibilite));
    }
    public IndisponibiliteResponse indisponibilite(Authentication actor,long id) {
        Long scope=authorization.requireZoneScope(actor);
        return mapper.indisponibilite(indisponibilites.findOne(periodScope(scope).and(id(id)))
                .orElseThrow(ResourceNotFoundException::new));
    }

    private Pageable pageable(ReferencePageRequest page) {
        return PageRequest.of(page.page(),page.size(),Sort.by("id").ascending());
    }
    private <T> Specification<T> id(long id) {
        return (root,query,cb)->cb.equal(root.get("id"),id);
    }
    private Specification<Zone> zoneScope(Long zoneId) {
        return (root,query,cb)->zoneId==null?cb.conjunction():cb.equal(root.get("id"),zoneId);
    }
    private Specification<Vehicule> vehiculeScope(Long zoneId) {
        return (root,query,cb)->zoneId==null?cb.conjunction():cb.equal(root.get("zone").get("id"),zoneId);
    }
    private Specification<Indisponibilite> periodScope(Long zoneId) {
        return (root,query,cb)->{
            if(zoneId==null)return cb.conjunction();
            var direct=root.join("zone",JoinType.LEFT);
            var vehicleZone=root.join("vehicule",JoinType.LEFT).join("zone",JoinType.LEFT);
            return cb.or(cb.equal(direct.get("id"),zoneId),cb.equal(vehicleZone.get("id"),zoneId));
        };
    }
}
