package com.plateforme.covoiturage.service.impl;

import com.plateforme.covoiturage.dto.request.ReferencePageRequest;
import com.plateforme.covoiturage.dto.response.ReferencePageResponse;
import com.plateforme.covoiturage.dto.response.ReservationResponse;
import com.plateforme.covoiturage.entity.Client;
import com.plateforme.covoiturage.entity.Reservation;
import com.plateforme.covoiturage.entity.Trajet;
import com.plateforme.covoiturage.entity.enums.StatutReservation;
import com.plateforme.covoiturage.entity.enums.TypeTrajet;
import com.plateforme.covoiturage.exception.BusinessRuleException;
import com.plateforme.covoiturage.exception.ResourceNotFoundException;
import com.plateforme.covoiturage.mapper.ReservationMapper;
import com.plateforme.covoiturage.repository.ReservationRepository;
import com.plateforme.covoiturage.security.ClientAuthorization;
import com.plateforme.covoiturage.service.PaymentService;
import com.plateforme.covoiturage.service.ReservationService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationServiceImpl implements ReservationService {
    private final ClientAuthorization authorization;
    private final JourneyLocks locks;
    private final BookingPolicy policy;
    private final AvailabilityService availability;
    private final ReservationRepository bookings;
    private final ReservationMapper mapper;
    private final EntityManager entityManager;
    private final Clock clock;
    private final SensitiveOperationJournal journal;
    private final PaymentService payments;

    /** Un client réserve un trajet public uniquement pour lui-même. */
    @Override
    @Transactional
    public ReservationResponse reserve(Authentication actor, long tripId) {
        var requester = authorization.require(actor);
        var trip = locks.trip(tripId);
        if (trip.getType() != TypeTrajet.PUBLIC) {
            throw new AccessDeniedException("Acces interdit");
        }
        return pending(trip, locks.client(requester.getId()));
    }

    /** RM18 : seul l'organisateur ajoute un passager éligible à son trajet privé. */
    @Override
    @Transactional
    public ReservationResponse addPrivate(Authentication actor, long tripId, long clientId) {
        var requester = authorization.require(actor);
        var trip = locks.trip(tripId);
        if (trip.getType() != TypeTrajet.PRIVE
                || !trip.getOrganisateur().getId().equals(requester.getId())) {
            throw new AccessDeniedException("Acces interdit");
        }
        return pending(trip, locks.client(clientId));
    }

    /** D4 : création EN_ATTENTE ; seule une confirmation payée occupe une place. */
    private ReservationResponse pending(Trajet trip, Client client) {
        policy.requireOpen(trip);
        policy.requireEligible(client, trip.getZone(), trip.getDepart());
        availability.requireAvailable(trip.getVehicule(), trip.getDepart(), trip.getArrivee());
        if (bookings.existsByTrajetIdAndClientIdAndStatutNot(
                trip.getId(), client.getId(), StatutReservation.ANNULEE)) {
            throw new BusinessRuleException("DUPLICATE_RESERVATION");
        }
        return mapper.toResponse(bookings.saveAndFlush(new Reservation(trip, client)));
    }

    /** Annule sa propre réservation avant le départ, en conservant paiements et dettes. */
    @Override
    @Transactional
    public ReservationResponse cancel(Authentication actor, long reservationId) {
        var requester = authorization.require(actor);
        var booking = bookings.findById(reservationId).orElseThrow(ResourceNotFoundException::new);
        if (!booking.getClient().getId().equals(requester.getId())) {
            throw new ResourceNotFoundException();
        }

        var trip = locks.trip(booking.getTrajet().getId());
        locks.client(requester.getId());
        entityManager.refresh(booking);
        // Recontrôle après verrouillage : la première lecture ne donne aucun droit d'écriture.
        if (!booking.getClient().getId().equals(requester.getId())
                || !booking.getTrajet().getId().equals(trip.getId())) {
            throw new ResourceNotFoundException();
        }

        var cancelledAt = clock.instant();
        if (!cancelledAt.isBefore(trip.getDepart())) {
            throw new BusinessRuleException("DEPARTURE_PASSED");
        }
        if (booking.getStatut() != StatutReservation.ANNULEE) {
            boolean confirmed = booking.getStatut() == StatutReservation.CONFIRMEE;
            booking.cancel(cancelledAt, policy.hasActivePenalty(requester.getId()));
            bookings.flush();
            if (confirmed) {
                payments.refundSeat(actor, booking, cancelledAt);
            }
            journal.record(SensitiveOperationJournal.Action.RESERVATION_ANNULEE, actor,
                    SensitiveOperationJournal.Resource.RESERVATION, reservationId);
        }
        return mapper.toResponse(booking);
    }

    /** Le client consulte uniquement son historique, annulations comprises. */
    @Override
    public ReferencePageResponse<ReservationResponse> mine(Authentication actor, ReferencePageRequest page) {
        var client = authorization.require(actor);
        var pagination = PageRequest.of(page.page(), page.size(), Sort.by("id"));
        return ReferencePageResponse.from(
                bookings.findByClientId(client.getId(), pagination).map(mapper::toResponse));
    }
}
