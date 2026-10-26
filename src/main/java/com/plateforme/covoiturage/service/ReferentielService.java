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

/** Référentiels et mutations explicitement autorisées, périmètre GLOBAL/LOCAL contrôlé côté serveur. */
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
    private final com.plateforme.covoiturage.security.ClientAuthorization clientAuthorization;
    private final TrajetRepository trips;
    private final SensitiveOperationJournal journal;
    private final JourneyLocks locks;
    private final jakarta.persistence.EntityManager entityManager;

    public AdminStatisticsResponse statistics(Authentication actor,Long zoneId){
        var p=trips.statistics(authorization.requireZoneScope(actor),zoneId);
        return new AdminStatisticsResponse(p.getTrajetsPlanifies(),p.getTrajetsComplets(),p.getTrajetsAnnules(),
            p.getReservationsEnAttente(),p.getReservationsConfirmees(),p.getReservationsAnnulees(),
            p.getPaiementsPlaces(),p.getPaiementsDettes(),p.getDettesRestantes());
    }

    public ReferencePageResponse<ZoneResponse> clientZones(Authentication actor,ReferencePageRequest page){
        var client=clientAuthorization.require(actor);
        var allowed=grants.allowedZones(client.getId());
        Specification<Zone> spec=(root,query,cb)->root.get("id").in(allowed);
        return ReferencePageResponse.from(zones.findAll(spec,pageable(page)).map(mapper::zone));
    }
    public ReferencePageResponse<VehiculeResponse> clientVehicles(Authentication actor,ReferencePageRequest page,Long zoneId){
        var client=clientAuthorization.require(actor);
        var allowed=grants.allowedZones(client.getId());
        Specification<Vehicule> spec=(root,query,cb)->root.get("zone").get("id").in(allowed);
        return ReferencePageResponse.from(vehicules.findAll(spec.and(vehiculeScope(zoneId)),pageable(page)).map(mapper::vehicule));
    }

    @Transactional
    public ZoneResponse createZone(Authentication actor,com.plateforme.covoiturage.dto.request.ZoneWriteRequest request){
        authorization.requireGlobal(actor); validateTimezone(request.fuseauHoraire());
        var zone=new Zone(request.nom().strip());
        zone.configure(request.nom().strip(),request.fuseauHoraire(),request.ouverteOccasionnels());
        zones.saveAndFlush(zone);
        journal.record(SensitiveOperationJournal.Action.ZONE_CREEE,actor,SensitiveOperationJournal.Resource.ZONE,zone.getId());
        return mapper.zone(zone);
    }
    @Transactional
    public ZoneResponse updateZone(Authentication actor,long id,com.plateforme.covoiturage.dto.request.ZoneWriteRequest request){
        authorization.requireGlobal(actor);validateTimezone(request.fuseauHoraire());
        var zone=zones.lockById(id).orElseThrow(ResourceNotFoundException::new);
        zone.configure(request.nom().strip(),request.fuseauHoraire(),request.ouverteOccasionnels());
        journal.record(SensitiveOperationJournal.Action.ZONE_CONFIGUREE,actor,SensitiveOperationJournal.Resource.ZONE,id);
        return mapper.zone(zone);
    }
    @Transactional
    public ZoneResponse zoneStatus(Authentication actor,long id,boolean active){
        authorization.requireGlobal(actor);
        var zone=zones.lockById(id).orElseThrow(ResourceNotFoundException::new);
        if(zone.isActif()!=active){
            zone.setActif(active);
            journal.record(active?SensitiveOperationJournal.Action.ZONE_ACTIVEE:SensitiveOperationJournal.Action.ZONE_DESACTIVEE,
                actor,SensitiveOperationJournal.Resource.ZONE,id);
        }
        return mapper.zone(zone);
    }
    @Transactional
    public void vipAccess(Authentication actor,long zoneId,long clientId,boolean allow){
        authorization.requireGlobal(actor);
        zones.lockById(zoneId).orElseThrow(ResourceNotFoundException::new);
        var client=clients.findById(clientId).orElseThrow(ResourceNotFoundException::new);
        if(client.getCategorie()!=com.plateforme.covoiturage.enums.CategorieClient.VIP)
            throw new com.plateforme.covoiturage.exception.BusinessRuleException("VIP_REQUIRED");
        boolean existed=grants.exists(clientId,zoneId);
        if(allow)grants.grant(clientId,zoneId);else grants.revoke(clientId,zoneId);
        if(existed!=allow)journal.record(allow?SensitiveOperationJournal.Action.AUTORISATION_VIP_ACCORDEE:SensitiveOperationJournal.Action.AUTORISATION_VIP_RETIREE,
            actor,SensitiveOperationJournal.Resource.ZONE,zoneId,clientId);
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

    /** Mutations validées le 26 octobre : mêmes périmètres que les lectures. */
    @Transactional
    public VehiculeResponse createVehicle(Authentication actor,com.plateforme.covoiturage.dto.request.CreateVehiculeRequest request) {
        zone(actor,request.zoneId()); // visibilité filtrée en SQL avant verrou
        var zone=locks.zone(request.zoneId());
        var vehicle=vehicules.saveAndFlush(new Vehicule(zone));
        journal.record(SensitiveOperationJournal.Action.VEHICULE_CREE,actor,SensitiveOperationJournal.Resource.VEHICULE,vehicle.getId());
        return mapper.vehicule(vehicle);
    }

    @Transactional
    public VehiculeResponse vehicleStatus(Authentication actor,long id,boolean active) {
        var visible=vehicule(actor,id);
        locks.zone(visible.zoneId());
        var vehicle=locks.vehicle(id);
        if(!vehicle.getZone().getId().equals(visible.zoneId()))
            throw new com.plateforme.covoiturage.exception.BusinessRuleException("RESOURCE_CHANGED");
        if(vehicle.isActif()!=active) {
            vehicle.setActif(active);
            journal.record(active?SensitiveOperationJournal.Action.VEHICULE_ACTIVE:SensitiveOperationJournal.Action.VEHICULE_DESACTIVE,
                actor,SensitiveOperationJournal.Resource.VEHICULE,id);
        }
        return mapper.vehicule(vehicle);
    }

    @Transactional
    public IndisponibiliteResponse createPeriod(Authentication actor,com.plateforme.covoiturage.dto.request.IndisponibiliteWriteRequest request) {
        validatePeriod(request);
        Long vehicleId=request.vehiculeId();
        long zoneId=vehicleId==null?request.zoneId():vehicule(actor,vehicleId).zoneId();
        zone(actor,zoneId);
        var zone=locks.zone(zoneId);
        var vehicle=vehicleId==null?null:locks.vehicle(vehicleId);
        if(vehicle!=null&&!vehicle.getZone().getId().equals(zoneId))
            throw new com.plateforme.covoiturage.exception.BusinessRuleException("RESOURCE_CHANGED");
        requireNoTrip(zoneId,vehicleId,request.debut(),request.fin());
        var period=indisponibilites.saveAndFlush(vehicle==null
            ?Indisponibilite.pourZone(zone,request.debut(),request.fin())
            :Indisponibilite.pourVehicule(vehicle,request.debut(),request.fin()));
        journal.record(SensitiveOperationJournal.Action.INDISPONIBILITE_CREEE,actor,SensitiveOperationJournal.Resource.INDISPONIBILITE,period.getId());
        return mapper.indisponibilite(period);
    }

    @Transactional
    public IndisponibiliteResponse updatePeriod(Authentication actor,long id,com.plateforme.covoiturage.dto.request.IndisponibiliteWriteRequest request) {
        validatePeriod(request);
        var period=managedPeriod(actor,id);
        if(!java.util.Objects.equals(request.zoneId(),period.getZone()==null?null:period.getZone().getId())
            ||!java.util.Objects.equals(request.vehiculeId(),period.getVehicule()==null?null:period.getVehicule().getId()))
            throw new com.plateforme.covoiturage.exception.BusinessRuleException("UNAVAILABILITY_TARGET_IMMUTABLE");
        long zoneId=period.getZone()!=null?period.getZone().getId():period.getVehicule().getZone().getId();
        requireNoTrip(zoneId,request.vehiculeId(),request.debut(),request.fin());
        period.configure(request.debut(),request.fin());
        indisponibilites.flush();
        journal.record(SensitiveOperationJournal.Action.INDISPONIBILITE_MODIFIEE,actor,SensitiveOperationJournal.Resource.INDISPONIBILITE,id);
        return mapper.indisponibilite(period);
    }

    @Transactional
    public void deletePeriod(Authentication actor,long id) {
        var period=managedPeriod(actor,id);
        indisponibilites.delete(period);
        indisponibilites.flush();
        journal.record(SensitiveOperationJournal.Action.INDISPONIBILITE_SUPPRIMEE,actor,SensitiveOperationJournal.Resource.INDISPONIBILITE,id);
    }

    private Indisponibilite managedPeriod(Authentication actor,long id) {
        var visible=indisponibilite(actor,id);
        long zoneId=visible.zoneId()!=null?visible.zoneId():vehicule(actor,visible.vehiculeId()).zoneId();
        locks.zone(zoneId);
        if(visible.vehiculeId()!=null) locks.vehicle(visible.vehiculeId());
        indisponibilites.lock(id).orElseThrow(ResourceNotFoundException::new);
        var period=indisponibilites.findById(id).orElseThrow(ResourceNotFoundException::new);
        entityManager.refresh(period);
        if(!java.util.Objects.equals(visible.zoneId(),period.getZone()==null?null:period.getZone().getId())
            ||!java.util.Objects.equals(visible.vehiculeId(),period.getVehicule()==null?null:period.getVehicule().getId())
            ||(period.getVehicule()!=null&&!period.getVehicule().getZone().getId().equals(zoneId)))
            throw new com.plateforme.covoiturage.exception.BusinessRuleException("RESOURCE_CHANGED");
        return period;
    }
    private void validatePeriod(com.plateforme.covoiturage.dto.request.IndisponibiliteWriteRequest request) {
        if(!request.isCibleValide()||request.debut()==null||request.fin()==null||!request.isPeriodeValide())
            throw new com.plateforme.covoiturage.exception.BusinessRuleException("INVALID_INTERVAL");
    }
    private void requireNoTrip(long zoneId,Long vehicleId,java.time.Instant start,java.time.Instant end) {
        if(trips.resourceOverlaps(zoneId,vehicleId,start,end))
            throw new com.plateforme.covoiturage.exception.BusinessRuleException("RESOURCE_HAS_TRIP");
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
