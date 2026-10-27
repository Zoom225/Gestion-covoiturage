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
    private final ItineraireProposeRepository itineraires;

    /**
     * Consulte les références géographiques communes après vérification du client actif, sans créer de trajet
     * ni accorder de droit sur une zone ; la création réelle conserve tous ses contrôles métier.
     * @return page ordonnée de lieux proposés, recherchés avant pagination et comptage
     */
    public ReferencePageResponse<ItineraireProposeResponse> clientItineraires(Authentication actor,
            ReferencePageRequest page, com.plateforme.covoiturage.dto.request.ItineraireSearchRequest search) {
        clientAuthorization.require(actor);
        return ReferencePageResponse.from(itineraires
            .findByOrigineContainingIgnoreCaseAndDestinationContainingIgnoreCase(
                search.origine(),search.destination(),pageable(page))
            .map(route -> new ItineraireProposeResponse(route.getId(),route.getOrigine(),route.getDestination())));
    }

    /**
     * Calcule les statistiques uniquement dans le périmètre GLOBAL ou LOCAL autorisé, avant agrégation et filtre facultatif de zone.
     */
    public AdminStatisticsResponse statistics(Authentication actor,Long zoneId){
        var p=trips.statistics(authorization.requireZoneScope(actor),zoneId);
        return new AdminStatisticsResponse(p.getTrajetsPlanifies(),p.getTrajetsComplets(),p.getTrajetsAnnules(),
            p.getReservationsEnAttente(),p.getReservationsConfirmees(),p.getReservationsAnnulees(),
            p.getPaiementsPlaces(),p.getPaiementsDettes(),p.getDettesRestantes());
    }

    /**
     * Liste avant pagination les seules zones auxquelles le client authentifié est autorisé à accéder (D3).
     */
    public ReferencePageResponse<ZoneResponse> clientZones(Authentication actor,ReferencePageRequest page){
        var client=clientAuthorization.require(actor);
        var allowed=grants.allowedZones(client.getId());
        Specification<Zone> spec=(root,query,cb)->root.get("id").in(allowed);
        return ReferencePageResponse.from(zones.findAll(spec,pageable(page)).map(mapper::zone));
    }
    /**
     * Liste les véhicules des seules zones autorisées au client, sans élargissement par le filtre demandé.
     */
    public ReferencePageResponse<VehiculeResponse> clientVehicles(Authentication actor,ReferencePageRequest page,Long zoneId){
        var client=clientAuthorization.require(actor);
        var allowed=grants.allowedZones(client.getId());
        Specification<Vehicule> spec=(root,query,cb)->root.get("zone").get("id").in(allowed);
        return ReferencePageResponse.from(vehicules.findAll(spec.and(vehiculeScope(zoneId)),pageable(page)).map(mapper::vehicule));
    }

    /**
     * Réserve à GLOBAL la création d'une zone avec fuseau explicite et ouverture OCCASIONNEL configurée (D1–D3).
     */
    @Transactional
    public ZoneResponse createZone(Authentication actor,com.plateforme.covoiturage.dto.request.ZoneWriteRequest request){
        authorization.requireGlobal(actor); validateTimezone(request.fuseauHoraire());
        var zone=new Zone(request.nom().strip());
        zone.configure(request.nom().strip(),request.fuseauHoraire(),request.ouverteOccasionnels());
        zones.saveAndFlush(zone);
        journal.record(SensitiveOperationJournal.Action.ZONE_CREEE,actor,SensitiveOperationJournal.Resource.ZONE,zone.getId());
        return mapper.zone(zone);
    }
    /**
     * Reconfigure une zone sous verrou pour GLOBAL, après validation du fuseau, sans modifier les droits administratifs (D1).
     */
    @Transactional
    public ZoneResponse updateZone(Authentication actor,long id,com.plateforme.covoiturage.dto.request.ZoneWriteRequest request){
        authorization.requireGlobal(actor);validateTimezone(request.fuseauHoraire());
        var zone=zones.lockById(id).orElseThrow(ResourceNotFoundException::new);
        zone.configure(request.nom().strip(),request.fuseauHoraire(),request.ouverteOccasionnels());
        journal.record(SensitiveOperationJournal.Action.ZONE_CONFIGUREE,actor,SensitiveOperationJournal.Resource.ZONE,id);
        return mapper.zone(zone);
    }
    /**
     * Active ou désactive une zone de façon idempotente pour GLOBAL et journalise seulement un changement réel (D1).
     */
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
    /**
     * Accorde ou retire idempotemment l'accès explicite d'un client VIP à une zone, uniquement pour GLOBAL (D3).
     */
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
    /**
     * Exige un identifiant de fuseau IANA reconnu sans attribuer de fuseau implicite à une zone (D2).
     */
    private void validateTimezone(String timezone){
        if(!java.time.ZoneId.getAvailableZoneIds().contains(timezone))
            throw new com.plateforme.covoiturage.exception.InvalidAccountRequestException();
    }

    /**
     * Liste les zones dans le périmètre administratif autorisé, avec filtrage SQL avant pagination (D1).
     */
    public ReferencePageResponse<ZoneResponse> zones(Authentication actor,ReferencePageRequest page) {
        Long scope=authorization.requireZoneScope(actor);
        return ReferencePageResponse.from(zones.findAll(zoneScope(scope),pageable(page)).map(mapper::zone));
    }
    /**
     * Retourne une zone uniquement dans le périmètre administratif, en confondant ressource invisible et absente.
     */
    public ZoneResponse zone(Authentication actor,long id) {
        Long scope=authorization.requireZoneScope(actor);
        return mapper.zone(zones.findOne(zoneScope(scope).and(id(id))).orElseThrow(ResourceNotFoundException::new));
    }
    /**
     * Liste les véhicules en combinant le périmètre administratif et le filtre de zone sans élargissement (RM49, RM51).
     */
    public ReferencePageResponse<VehiculeResponse> vehicules(Authentication actor,ReferencePageRequest page,Long zoneId) {
        Long scope=authorization.requireZoneScope(actor);
        var spec=vehiculeScope(scope).and(vehiculeScope(zoneId));
        return ReferencePageResponse.from(vehicules.findAll(spec,pageable(page)).map(mapper::vehicule));
    }
    /**
     * Retourne un véhicule visible dans le périmètre administratif, sans divulgation hors zone LOCAL.
     */
    public VehiculeResponse vehicule(Authentication actor,long id) {
        Long scope=authorization.requireZoneScope(actor);
        return mapper.vehicule(vehicules.findOne(vehiculeScope(scope).and(id(id)))
                .orElseThrow(ResourceNotFoundException::new));
    }
    /**
     * Liste les indisponibilités dans le périmètre administratif avant pagination et comptage (RM49, RM51).
     */
    public ReferencePageResponse<IndisponibiliteResponse> indisponibilites(Authentication actor,ReferencePageRequest page,
                                                                          Long zoneId,Long vehiculeId) {
        Long scope=authorization.requireZoneScope(actor);
        var spec=periodScope(scope).and(periodScope(zoneId));
        if(vehiculeId!=null) {
            spec=spec.and((root,query,cb)->cb.equal(root.join("vehicule",JoinType.LEFT).get("id"),vehiculeId));
        }
        return ReferencePageResponse.from(indisponibilites.findAll(spec,pageable(page)).map(mapper::indisponibilite));
    }
    /**
     * Retourne une indisponibilité visible dans le périmètre GLOBAL ou LOCAL autorisé.
     */
    public IndisponibiliteResponse indisponibilite(Authentication actor,long id) {
        Long scope=authorization.requireZoneScope(actor);
        return mapper.indisponibilite(indisponibilites.findOne(periodScope(scope).and(id(id)))
                .orElseThrow(ResourceNotFoundException::new));
    }

    /**
     * Crée un véhicule dans une zone administrativement autorisée sous verrou, sans propriété CLIENT ni transfert de zone inventé (#25).
     */
    @Transactional
    public VehiculeResponse createVehicle(Authentication actor,com.plateforme.covoiturage.dto.request.CreateVehiculeRequest request) {
        zone(actor,request.zoneId()); // visibilité filtrée en SQL avant verrou
        var zone=locks.zone(request.zoneId());
        var vehicle=vehicules.saveAndFlush(new Vehicule(zone));
        journal.record(SensitiveOperationJournal.Action.VEHICULE_CREE,actor,SensitiveOperationJournal.Resource.VEHICULE,vehicle.getId());
        return mapper.vehicule(vehicle);
    }

    /**
     * Active ou désactive idempotemment un véhicule autorisé sans annuler ses trajets existants (#25).
     */
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

    /**
     * Crée une indisponibilité de zone ou véhicule autorisée sans conflit avec un trajet existant, sous les verrous partagés (#25).
     */
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

    /**
     * Modifie les dates d'une indisponibilité autorisée sans changer sa cible ni créer de conflit avec les trajets (#25).
     */
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

    /**
     * Retire uniquement une indisponibilité visible et verrouillée dans le périmètre administratif, sans supprimer de trajet (#25).
     */
    @Transactional
    public void deletePeriod(Authentication actor,long id) {
        var period=managedPeriod(actor,id);
        indisponibilites.delete(period);
        indisponibilites.flush();
        journal.record(SensitiveOperationJournal.Action.INDISPONIBILITE_SUPPRIMEE,actor,SensitiveOperationJournal.Resource.INDISPONIBILITE,id);
    }

    /**
     * Vérifie la visibilité puis verrouille et relit l'indisponibilité et sa cible afin de refuser un changement concurrent de ressource.
     */
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
    /**
     * Exige une seule cible et une période dont la fin suit strictement le début avant une mutation d'indisponibilité.
     */
    private void validatePeriod(com.plateforme.covoiturage.dto.request.IndisponibiliteWriteRequest request) {
        if(!request.isCibleValide()||request.debut()==null||request.fin()==null||!request.isPeriodeValide())
            throw new com.plateforme.covoiturage.exception.BusinessRuleException("INVALID_INTERVAL");
    }
    /**
     * Refuse une indisponibilité qui chevauche un trajet existant de la zone ou du véhicule concerné.
     */
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
    /**
     * Construit le périmètre SQL des zones autorisées sans rendre une zone LOCAL visible hors de son périmètre.
     */
    private Specification<Zone> zoneScope(Long zoneId) {
        return (root,query,cb)->zoneId==null?cb.conjunction():cb.equal(root.get("id"),zoneId);
    }
    /**
     * Filtre les véhicules par leur zone avant de charger les données ou de calculer la pagination.
     */
    private Specification<Vehicule> vehiculeScope(Long zoneId) {
        return (root,query,cb)->zoneId==null?cb.conjunction():cb.equal(root.get("zone").get("id"),zoneId);
    }
    /**
     * Filtre les indisponibilités par zone directe ou zone du véhicule, sans révéler de ressource hors périmètre LOCAL.
     */
    private Specification<Indisponibilite> periodScope(Long zoneId) {
        return (root,query,cb)->{
            if(zoneId==null)return cb.conjunction();
            var direct=root.join("zone",JoinType.LEFT);
            var vehicleZone=root.join("vehicule",JoinType.LEFT).join("zone",JoinType.LEFT);
            return cb.or(cb.equal(direct.get("id"),zoneId),cb.equal(vehicleZone.get("id"),zoneId));
        };
    }
}
