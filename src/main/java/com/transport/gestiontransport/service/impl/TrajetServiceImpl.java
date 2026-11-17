package com.transport.gestiontransport.service.impl;

import com.transport.gestiontransport.dto.request.TrajetRequest;
import com.transport.gestiontransport.entity.*;
import com.transport.gestiontransport.entity.enums.*;
import com.transport.gestiontransport.exception.BusinessException;
import com.transport.gestiontransport.exception.ResourceNotFoundException;
import com.transport.gestiontransport.mapper.TrajetMapper;
import com.transport.gestiontransport.dto.request.CreateTrajetRequest;
import com.transport.gestiontransport.dto.response.TrajetDto;
import com.transport.gestiontransport.repository.*;
import com.transport.gestiontransport.service.TrajetService;
import com.transport.gestiontransport.service.VoyageurService;
import com.transport.gestiontransport.service.VehiculeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import com.transport.gestiontransport.service.ReservationService;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrajetServiceImpl implements TrajetService {
    private static final String VILLES_DISTINCTES_MESSAGE = "Choisissez deux villes différentes : la ville de départ et la destination ne peuvent pas être identiques.";

    private final TrajetRepository trajetRepository;
    private final VoyageurRepository voyageurRepository;
    private final ReservationService reservationService;
    private final EntityManager entityManager;
    private final AdminAuthorizationService adminAuthorizationService;
    private final VoyageurCourantService voyageurCourantService;
    private final ReservationRepository reservationRepository;
    private final IndisponibiliteServiceRepository indisponibiliteServiceRepository;
    private final VehiculeService vehiculeService;
    private final VoyageurService voyageurService;
    private final TrajetMapper trajetMapper;

    @Override
    @Transactional
    public TrajetDto createTrajet(CreateTrajetRequest request, String username) {
        Voyageur chauffeur = voyageurRepository.findByMatriculeIgnoreCase(username)
                .orElseThrow(() -> new ResourceNotFoundException("Voyageur non trouvé pour l'utilisateur: " + username));
        if (request.chauffeurId() != null && !request.chauffeurId().equals(chauffeur.getId())) {
            throw new BusinessException("Le chauffeur est déterminé par votre authentification.");
        }
        return creerPourChauffeur(request, chauffeur);
    }

    @Override
    @Transactional
    public TrajetDto createTrajetForAdmin(CreateTrajetRequest request) {
        if (request.chauffeurId() == null) throw new BusinessException("Sélectionnez un chauffeur existant.");
        Voyageur chauffeur = voyageurService.getById(request.chauffeurId());
        adminAuthorizationService.checkVoyageurAccess(chauffeur);
        adminAuthorizationService.checkVehiculeAccess(vehiculeService.getById(request.vehiculeId()));
        return creerPourChauffeur(request, chauffeur);
    }

    private TrajetDto creerPourChauffeur(CreateTrajetRequest request, Voyageur chauffeur) {
        if (request.villeDepart() == null || request.villeArrivee() == null) {
            throw new BusinessException("La ville de départ et la destination sont obligatoires.");
        }
        verifierVillesDistinctes(request.villeDepart(), request.villeArrivee());
        Vehicule vehicule = vehiculeService.getById(request.vehiculeId());
        entityManager.lock(chauffeur, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(chauffeur);
        entityManager.lock(vehicule, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(vehicule);
        if (Boolean.FALSE.equals(vehicule.getDisponible())) throw new BusinessException("Ce véhicule est indisponible.");
        if (!Boolean.TRUE.equals(chauffeur.getPeutProposerTrajet())) {
            throw new BusinessException("Ce voyageur n'est pas autorisé à proposer un trajet.");
        }
        if (voyageurService.hasOutstandingBalance(chauffeur.getId())) {
            throw new BusinessException("Vous avez un solde impayé et ne pouvez pas créer de trajet.");
        }
        if (voyageurService.hasActiveRestriction(chauffeur.getId())) {
            throw new BusinessException("Vous avez une restriction active et ne pouvez pas créer de trajet.");
        }
        LocalDateTime debut = request.trajetDate();
        if (debut == null || !debut.isAfter(LocalDateTime.now())) {
            throw new BusinessException("Le départ doit être futur.");
        }
        TypeTrajet type;
        try { type = TypeTrajet.valueOf(request.trajetType()); }
        catch (IllegalArgumentException | NullPointerException ex) { throw new BusinessException("Type de trajet invalide."); }
        validateAgenceAccessForOrganizer(chauffeur, vehicule);
        LocalDateTime fin = debut.plusMinutes(vehicule.getAgence().getDureeTrajetMinutes());
        validateAgenceNotClosed(vehicule, debut.toLocalDate());
        validateAgenceOpeningHours(vehicule, debut.toLocalTime(), fin.toLocalTime());
        if (!isSlotAvailable(vehicule, debut, fin)) {
            throw new BusinessException("Ce créneau est déjà réservé sur le vehicule : " + vehicule.getId());
        }
        if (trajetRepository.existsChauffeurOverlap(chauffeur.getId(), debut, fin, 0L)) {
            throw new BusinessException("Le chauffeur effectue déjà un trajet à cet horaire.");
        }
        Trajet trajet = Trajet.builder().chauffeur(chauffeur).vehicule(vehicule)
                .dateDebut(debut).dateFin(fin).villeDepart(request.villeDepart()).villeArrivee(request.villeArrivee())
                .typeTrajet(type).statut(StatutTrajet.PLANIFIE).nbPassagersActuels(0)
                .publicationAutomatiqueAutorisee(request.publicationAutomatiqueAutorisee())
                .prixTotal(PRIX_TRAJET_EUROS.doubleValue()).prixParPassager(PRIX_CLIENT_EUROS.doubleValue()).build();
        return trajetMapper.toTrajetDto(trajetRepository.save(trajet));
    }

    @Override
    public List<TrajetDto> findAllTrajets() {
        return trajetRepository.findAll().stream()
                .map(trajetMapper::toTrajetDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<TrajetDto> getPublicAvailableTrajets() {
        return searchPublicTrajets(null, null, null);
    }

    @Override
    public List<TrajetDto> searchPublicTrajets(VilleBelge depart, VilleBelge arrivee, LocalDate date) {
        verifierVillesDistinctes(depart, arrivee);
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        Voyageur voyageur = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_VOYAGEUR".equals(a.getAuthority())) ? voyageurCourantService.currentVoyageur() : null;
        return trajetRepository.findByTypeTrajetAndStatut(TypeTrajet.PUBLIC, StatutTrajet.PLANIFIE).stream()
                .filter(TrajetService::estQualifiePourCovoiturage)
                .filter(t -> t.getDateDebut().isAfter(LocalDateTime.now()) && t.getNbPassagersActuels() < Trajet.MAX_CLIENTS)
                .filter(t -> depart == null || t.getVilleDepart() == depart)
                .filter(t -> arrivee == null || t.getVilleArrivee() == arrivee)
                .filter(t -> date == null || t.getDateDebut().toLocalDate().equals(date))
                .filter(t -> voyageur == null || voyageur.getTypeVoyageur() != TypeVoyageur.LOCAL
                        || (voyageur.getAgence() != null && voyageur.getAgence().getId().equals(t.getVehicule().getAgence().getId())))
                .map(trajetMapper::toTrajetDto).toList();
    }

    private void verifierVillesDistinctes(VilleBelge depart, VilleBelge arrivee) {
        if (depart != null && depart == arrivee) throw new BusinessException(VILLES_DISTINCTES_MESSAGE);
    }

    @Override
    public List<TrajetDto> findByOrganisateur(Long organisateurId) {
        return trajetRepository.findByChauffeurId(organisateurId)
                .stream()
                .map(trajetMapper::toTrajetDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<TrajetDto> findByAgence(Long agenceId) {
        return trajetRepository.findByVehiculeAgenceId(agenceId)
                .stream()
                .map(trajetMapper::toTrajetDto)
                .collect(Collectors.toList());
    }

    @Override
    public Trajet getTrajetEntityById(Long id) {
        return trajetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trajet non trouvé avec l'ID : " + id));
    }

    @Override
    public TrajetDto getTrajetDtoById(Long id) {
        checkReadAccess(id);
        return trajetMapper.toTrajetDto(getTrajetEntityById(id));
    }

    @Override
    public void checkReadAccess(Long trajetId) {
        Trajet trajet = getTrajetEntityById(trajetId);
        if (adminAuthorizationService.isAdmin()) {
            adminAuthorizationService.checkTrajetAccess(trajetId);
            return;
        }
        Voyageur voyageur = voyageurCourantService.currentVoyageur();
        if (trajet.getChauffeur().getId().equals(voyageur.getId())) return;
        if (trajet.getTypeTrajet() == TypeTrajet.PRIVE
                && !reservationRepository.existsByTrajetIdAndVoyageurId(trajetId, voyageur.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Ce trajet privé nécessite une invitation.");
        }
        if (voyageur.getTypeVoyageur() == TypeVoyageur.LOCAL && (voyageur.getAgence() == null
                || !voyageur.getAgence().getId().equals(trajet.getVehicule().getAgence().getId()))) {
            throw new org.springframework.security.access.AccessDeniedException("Ce trajet se situe hors de votre zone autorisée.");
        }
    }

    @Override
    @Transactional
    public TrajetDto updateTrajet(Long trajetId, TrajetRequest request) {
        Trajet trajet = getTrajetEntityById(trajetId);
        entityManager.lock(trajet, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(trajet);
        entityManager.lock(trajet.getChauffeur(), LockModeType.PESSIMISTIC_WRITE);

        // Regle metier : un trajet annule ne peut plus etre modifie.
        if (trajet.getStatut() == StatutTrajet.ANNULE) {
            throw new BusinessException("Impossible de modifier un trajet annulé.");
        }
        // Regle metier : seul l'organisateur peut modifier son trajet.
        if (!trajet.getChauffeur().getId().equals(request.getOrganisateurId())) {
            throw new BusinessException("Seul l'organisateur peut modifier ce trajet.");
        }

        Vehicule vehicule = vehiculeService.getById(request.getVehiculeId());
        entityManager.lock(vehicule, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(vehicule);
        LocalDateTime dateDebut = request.getDate().atTime(request.getHeureDebut());
        if (Boolean.FALSE.equals(vehicule.getDisponible())) throw new BusinessException("Ce véhicule est indisponible.");
        LocalDateTime dateFin = dateDebut.plusMinutes(vehicule.getAgence().getDureeTrajetMinutes());

        validateAgenceNotClosed(vehicule, dateDebut.toLocalDate());
        validateAgenceOpeningHours(vehicule, dateDebut.toLocalTime(), dateFin.toLocalTime());

        // Regle metier : la modification ne doit pas creer de chevauchement de creneau.
        if (!isSlotAvailableExcluding(vehicule, dateDebut, dateFin, trajetId)) {
            throw new BusinessException("Ce créneau est déjà réservé sur le vehicule : " + vehicule.getId());
        }

        if (!dateDebut.isAfter(LocalDateTime.now())
                || trajetRepository.existsChauffeurOverlap(trajet.getChauffeur().getId(), dateDebut, dateFin, trajetId)) {
            throw new BusinessException("Horaire passé ou incompatible pour ce chauffeur.");
        }
        trajet.setVehicule(vehicule);
        trajet.setDateDebut(dateDebut);
        trajet.setDateFin(dateFin);
        trajet.setTypeTrajet(request.getTypeTrajet());

        return trajetMapper.toTrajetDto(trajetRepository.save(trajet));
    }

    @Override
    @Transactional
    public void cancelTrajet(Long trajetId, Long requesterId) {
        reservationService.cancelTrajet(trajetId, requesterId);
    }

    @Override
    @Transactional
    public void checkAndConvertExpiredPrivateTrajets() {
        LocalDateTime maintenant = LocalDateTime.now();
        List<Trajet> trajets24h = trajetRepository.findByDateDebutBetweenAndStatut(maintenant, maintenant.plusHours(24), StatutTrajet.PLANIFIE);

        // 1. Conversion automatique si autorisée par le chauffeur
        trajets24h.stream()
                .filter(t -> t.getTypeTrajet() == TypeTrajet.PRIVE && t.isPublicationAutomatiqueAutorisee())
                .forEach(t -> convertToPublic(t.getId()));

        // 2. Trajet privé sans consentement de conversion restant incomplet (< 4 passagers) :
        // Le chauffeur est tenu responsable du montant complet des 4 places (100€)
        trajets24h.stream()
                .filter(t -> t.getTypeTrajet() == TypeTrajet.PRIVE && !t.isPublicationAutomatiqueAutorisee())
                .filter(t -> t.getNbPassagersActuels() < Trajet.MAX_CLIENTS)
                .forEach(t -> imputerPenaliteTrajetPriveIncomplet(t.getId()));
    }

    @Override
    @Transactional
    public void imputerPenaliteTrajetPriveIncomplet(Long trajetId) {
        Trajet trajet = getTrajetEntityById(trajetId);
        if (trajet.getTypeTrajet() != TypeTrajet.PRIVE) {
            throw new BusinessException("Seuls les trajets privés sont soumis à la pénalité de complétude.");
        }
        if (trajet.getNbPassagersActuels() < Trajet.MAX_CLIENTS) {
            Voyageur chauffeur = trajet.getChauffeur();
            if (chauffeur != null && chauffeur.getSolde() == 0.0) {
                entityManager.lock(chauffeur, LockModeType.PESSIMISTIC_WRITE);
                chauffeur.setSolde(PRIX_TRAJET_EUROS.doubleValue());
                voyageurRepository.save(chauffeur);
                log.info("Règle métier : pénalité de {}€ imputée au chauffeur {} pour trajet privé {} incomplet.",
                        PRIX_TRAJET_EUROS, chauffeur.getMatricule(), trajet.getId());
            }
        }
    }

    @Override
    @Transactional
    public void convertToPublic(Long trajetId) {
        Trajet trajet = getTrajetEntityById(trajetId);
        entityManager.lock(trajet, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(trajet);
        LocalDateTime maintenant = LocalDateTime.now();
        if (trajet.getTypeTrajet() != TypeTrajet.PRIVE || !trajet.isPublicationAutomatiqueAutorisee()
                || trajet.getStatut() != StatutTrajet.PLANIFIE
                || !trajet.getDateDebut().isAfter(maintenant) || trajet.getDateDebut().isAfter(maintenant.plusHours(24))) {
            throw new BusinessException("Publication autorisée uniquement avec le consentement du chauffeur dans les 24 heures avant départ.");
        }
        trajet.setTypeTrajet(TypeTrajet.PUBLIC);
        trajet.setDateConversionPublic(maintenant);
        trajetRepository.save(trajet);
    }

    //la règle des 15 minutes entre deux trajets
    private boolean isSlotAvailable(Vehicule vehicule, LocalDateTime start, LocalDateTime end) {
        int breakMinutes = vehicule.getAgence().getDureeEntreTrajetMinutes();
        List<Trajet> existingTrajets = trajetRepository.findOverlappingTrajets(
                vehicule.getId(),
                start.minusMinutes(breakMinutes),
                end.plusMinutes(breakMinutes),
                StatutTrajet.ANNULE
        );
        return existingTrajets.isEmpty();
    }

    private boolean isSlotAvailableExcluding(Vehicule vehicule, LocalDateTime start, LocalDateTime end, Long excludedTrajetId) {
        int breakMinutes = vehicule.getAgence().getDureeEntreTrajetMinutes();
        List<Trajet> existingTrajets = trajetRepository.findOverlappingTrajets(
                vehicule.getId(),
                start.minusMinutes(breakMinutes),
                end.plusMinutes(breakMinutes),
                StatutTrajet.ANNULE
        );
        return existingTrajets.stream().allMatch(m -> m.getId().equals(excludedTrajetId));
    }

    //methode fermeture globaux de agence
    private void validateAgenceNotClosed(Vehicule vehicule, LocalDate date) {
        boolean closedGlobally = indisponibiliteServiceRepository.existsByDateAndGlobalTrue(date);
        boolean closedForAgence = indisponibiliteServiceRepository.existsByDateAndAgenceId(date, vehicule.getAgence().getId());
        // Regle metier : aucun trajet ne peut etre cree sur une agence ferme .jour fermeture globaux.
        if (closedGlobally || closedForAgence) {
            throw new BusinessException("Le agence est fermé à la date : " + date);
        }
    }
        //methode les horaires d'ouverture propre pour une agence
    private void validateAgenceOpeningHours(Vehicule vehicule, LocalTime heureDebut, LocalTime heureFin) {
        LocalTime openingTime = vehicule.getAgence().getHeureOuverture();
        LocalTime closingTime = vehicule.getAgence().getHeureFermeture();
        // Regle metier : le trajet doit respecter les horaires d'ouverture de cette agence.
        if (heureDebut.isBefore(openingTime) || heureFin.isAfter(closingTime)) {
            throw new BusinessException("Le trajet est en dehors des heures d'ouverture de cette agence (" + openingTime + " - " + closingTime + ").");
        }
    }

    private void validateAgenceAccessForOrganizer(Voyageur organisateur, Vehicule vehicule) {
        if (organisateur.getTypeVoyageur() == TypeVoyageur.LOCAL) {
            // Regle metier : un voyageur AGENCE ne peut creer un trajet que sur sa propre agence.
            if (organisateur.getAgence() == null || !organisateur.getAgence().getId().equals(vehicule.getAgence().getId())) {
                throw new BusinessException("Un voyageur AGENCE ne peut créer un trajet que sur sa propre agence.");
            }
        }
    }
}
