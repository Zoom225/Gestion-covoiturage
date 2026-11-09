package com.plateforme.covoiturage.service.impl;

import com.plateforme.covoiturage.dto.request.ReferencePageRequest;
import com.plateforme.covoiturage.dto.response.DetteResponse;
import com.plateforme.covoiturage.dto.response.PaymentResponse;
import com.plateforme.covoiturage.dto.response.ReferencePageResponse;
import com.plateforme.covoiturage.entity.PaiementDette;
import com.plateforme.covoiturage.entity.PaiementPlace;
import com.plateforme.covoiturage.entity.Reservation;
import com.plateforme.covoiturage.entity.enums.StatutReservation;
import com.plateforme.covoiturage.exception.BusinessRuleException;
import com.plateforme.covoiturage.exception.ResourceNotFoundException;
import com.plateforme.covoiturage.mapper.PaymentMapper;
import com.plateforme.covoiturage.payment.PaymentGateway;
import com.plateforme.covoiturage.repository.DetteTrajetRepository;
import com.plateforme.covoiturage.repository.PaiementDetteRepository;
import com.plateforme.covoiturage.repository.PaiementPlaceRepository;
import com.plateforme.covoiturage.repository.ReservationRepository;
import com.plateforme.covoiturage.security.ClientAuthorization;
import com.plateforme.covoiturage.service.PaymentService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Paiements de places et de dettes, avec conservation de leur historique. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {
    private static final BigDecimal SEAT_PRICE = new BigDecimal("20.00");
    private static final Duration REFUND_NOTICE = Duration.ofHours(24);

    private final ClientAuthorization authorization;
    private final JourneyLocks locks;
    private final BookingPolicy policy;
    private final AvailabilityService availability;
    private final ReservationRepository reservations;
    private final DetteTrajetRepository debts;
    private final PaiementPlaceRepository seatPayments;
    private final PaiementDetteRepository debtPayments;
    private final PaymentGateway gateway;
    private final PaymentMapper mapper;
    private final EntityManager entityManager;
    private final Clock clock;
    private final SensitiveOperationJournal journal;

    /** Remboursement à H-24 ou avant, uniquement sans pénalité, dans la transaction d'annulation. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void refundSeat(Authentication actor, Reservation booking, Instant cancelledAt) {
        if (booking.getStatut() != StatutReservation.ANNULEE) {
            throw new BusinessRuleException("RESERVATION_NOT_CANCELLED");
        }
        if (booking.isAnnuleAvecPenalite() || policy.hasActivePenalty(booking.getClient().getId())) {
            journal.record(SensitiveOperationJournal.Action.REMBOURSEMENT_REFUSE_PENALITE, actor,
                    SensitiveOperationJournal.Resource.RESERVATION, booking.getId());
            return;
        }
        // À moins de 24 heures du départ, le paiement reste acquis.
        Instant refundDeadline = booking.getTrajet().getDepart().minus(REFUND_NOTICE);
        if (cancelledAt.isAfter(refundDeadline)) {
            return;
        }

        var payment = seatPayments.findByReservationId(booking.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "La réservation confirmée doit avoir un paiement"));
        if (payment.getRembourseLe() != null) {
            return; // Une annulation répétée ne rembourse pas une deuxième fois.
        }

        gateway.refund(payment.getCleIdempotence(), payment.getMontant());
        payment.refund(cancelledAt);
        journal.record(SensitiveOperationJournal.Action.PLACE_REMBOURSEE, actor,
                SensitiveOperationJournal.Resource.PAIEMENT_PLACE, payment.getId());
    }

    /** D4, RM27, RM40 : 20 EUR par passager ; le quatrième paiement rend le trajet complet. */
    @Override
    @Transactional
    public PaymentResponse paySeat(Authentication actor, long reservationId, UUID key) {
        var requester = authorization.require(actor);
        var booking = reservations.findById(reservationId).orElseThrow(ResourceNotFoundException::new);
        // Chacun paie sa propre place, y compris l'organisateur ; le chauffeur est fourni en plus.
        if (!booking.getClient().getId().equals(requester.getId())) {
            throw new ResourceNotFoundException();
        }

        // Ordre partagé avec la réservation et l'annulation pour protéger la dernière place.
        var trip = locks.trip(booking.getTrajet().getId());
        var client = locks.client(requester.getId());
        entityManager.refresh(booking);

        // Un rejeu du même paiement retourne sa preuve, même si le trajet est devenu complet.
        var existingPayment = seatPayments.findByCleIdempotence(key).orElse(null);
        if (existingPayment != null) {
            if (!existingPayment.getReservation().getId().equals(reservationId)) {
                throw new BusinessRuleException("IDEMPOTENCY_CONFLICT");
            }
            return mapper.place(existingPayment);
        }
        if (seatPayments.findByReservationId(reservationId).isPresent()) {
            throw new BusinessRuleException("ALREADY_PAID");
        }

        long confirmedPassengers = policy.requireOpen(trip);
        policy.requireEligible(client, trip.getZone(), trip.getDepart());
        if (booking.getStatut() != StatutReservation.EN_ATTENTE) {
            throw new BusinessRuleException("RESERVATION_NOT_PENDING");
        }
        availability.requireAvailable(trip.getVehicule(), trip.getDepart(), trip.getArrivee());

        String mode = gateway.validate(key, SEAT_PRICE).name();
        var payment = seatPayments.save(
                new PaiementPlace(booking, key, SEAT_PRICE, mode, clock.instant()));
        booking.confirm();
        journal.record(SensitiveOperationJournal.Action.PLACE_PAYEE, actor,
                SensitiveOperationJournal.Resource.PAIEMENT_PLACE, payment.getId());
        journal.record(SensitiveOperationJournal.Action.RESERVATION_CONFIRMEE, actor,
                SensitiveOperationJournal.Resource.RESERVATION, booking.getId());

        // PostgreSQL exige la confirmation pendant PLANIFIE, puis le passage à COMPLET.
        entityManager.flush();
        if (confirmedPassengers == BookingPolicy.MAX_CONFIRMED_PASSENGERS - 1) {
            trip.markFull();
            entityManager.flush();
            journal.record(SensitiveOperationJournal.Action.TRAJET_COMPLET, actor,
                    SensitiveOperationJournal.Resource.TRAJET, trip.getId());
        }
        return mapper.place(payment);
    }

    /** RM44, RM52–54 : règlement partiel autorisé ; la dette reste active jusqu'au solde intégral. */
    @Override
    @Transactional
    public PaymentResponse payDebt(Authentication actor, long debtId, UUID key, BigDecimal amount) {
        var requester = authorization.require(actor);
        locks.client(requester.getId());
        debts.lock(debtId).orElseThrow(ResourceNotFoundException::new);
        var debt = debts.findById(debtId).orElseThrow(ResourceNotFoundException::new);
        entityManager.refresh(debt);
        if (!debt.getClient().getId().equals(requester.getId())) {
            throw new ResourceNotFoundException();
        }
        if (amount == null) {
            throw new BusinessRuleException("INVALID_DEBT_AMOUNT");
        }

        // Même clé, même dette et même montant : aucune nouvelle écriture ni nouveau débit.
        var existingPayment = debtPayments.findByCleIdempotence(key).orElse(null);
        if (existingPayment != null) {
            if (!existingPayment.getDette().getId().equals(debtId)
                    || existingPayment.getMontant().compareTo(amount) != 0) {
                throw new BusinessRuleException("IDEMPOTENCY_CONFLICT");
            }
            return mapper.debt(existingPayment);
        }

        // La règle de montant est portée par la dette et vérifiée avant tout appel au paiement.
        debt.requireValidPaymentAmount(amount);
        String mode = gateway.validate(key, amount).name();
        debt.settle(amount);
        var payment = debtPayments.save(new PaiementDette(debt, key, amount, mode, clock.instant()));
        entityManager.flush();

        journal.record(SensitiveOperationJournal.Action.DETTE_REGLEE, actor,
                SensitiveOperationJournal.Resource.PAIEMENT_DETTE, payment.getId());
        if ("REGULARISEE".equals(debt.getStatut())) {
            journal.record(SensitiveOperationJournal.Action.DETTE_REGULARISEE, actor,
                    SensitiveOperationJournal.Resource.DETTE, debtId);
        }
        // Régler une dette ne réactive jamais un compte ou un client désactivé.
        return mapper.debt(payment);
    }

    /** Historique du seul client authentifié, avec un total et une page lus dans le même état. */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReferencePageResponse<PaymentResponse> history(Authentication actor, ReferencePageRequest page) {
        var client = authorization.require(actor);
        return ReferencePageResponse.from(seatPayments.history(
                client.getId(), PageRequest.of(page.page(), page.size())).map(mapper::history));
    }

    /** Les dettes restent consultables ; aucune expiration ni suppression implicite. */
    @Override
    public ReferencePageResponse<DetteResponse> mine(Authentication actor, ReferencePageRequest page) {
        var client = authorization.require(actor);
        var pagination = PageRequest.of(page.page(), page.size(), Sort.by("id"));
        return ReferencePageResponse.from(debts.findByClientId(client.getId(), pagination).map(mapper::dette));
    }
}
