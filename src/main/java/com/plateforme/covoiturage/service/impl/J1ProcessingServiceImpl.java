package com.plateforme.covoiturage.service.impl;

import com.plateforme.covoiturage.entity.DetteTrajet;
import com.plateforme.covoiturage.entity.Trajet;
import com.plateforme.covoiturage.entity.enums.StatutTrajet;
import com.plateforme.covoiturage.entity.enums.TypeTrajet;
import com.plateforme.covoiturage.repository.DetteTrajetRepository;
import com.plateforme.covoiturage.repository.ReservationRepository;
import com.plateforme.covoiturage.repository.TrajetRepository;
import com.plateforme.covoiturage.service.J1ProcessingService;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class J1ProcessingServiceImpl implements J1ProcessingService {
    private final TrajetRepository trips;
    private final ReservationRepository reservations;
    private final DetteTrajetRepository debts;
    private final JourneyLocks locks;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final SensitiveOperationJournal journal;

    /** Rattrape les contrôles J-1 manqués ; chaque trajet est traité dans sa propre transaction. */
    @Override
    public int catchUp() {
        int processed = 0;
        long after = 0;
        Instant now = clock.instant();
        // Filtre large en base ; l'échéance exacte est recontrôlée sous verrou.
        Instant until = now.atZone(BookingPolicy.BUSINESS_ZONE).toLocalDate().plusDays(2)
                .atStartOfDay(BookingPolicy.BUSINESS_ZONE).toInstant();

        while (true) {
            var candidates = trips.j1Candidates(after, until, PageRequest.of(0, 100));
            if (candidates.isEmpty()) {
                return processed;
            }
            for (Long id : candidates) {
                after = id;
                try {
                    Boolean changed = transactions.execute(status -> {
                        var trip = locks.trip(id);
                        locks.client(trip.getOrganisateur().getId());
                        return controler(trip);
                    });
                    if (Boolean.TRUE.equals(changed)) {
                        processed++;
                    }
                } catch (RuntimeException failure) {
                    log.warn("J1 : traitement du trajet {} à reprendre", id);
                }
            }
        }
    }

    /** Reconstitue la situation à J-1 ; une annulation ultérieure ne relance pas ce contrôle. */
    private boolean controler(Trajet trip) {
        Instant now = clock.instant();
        Instant deadline = BookingPolicy.j1Deadline(trip.getDepart());
        if (trip.getType() != TypeTrajet.PRIVE || trip.getStatut() == StatutTrajet.ANNULE
                || trip.getTraiteJ1Le() != null || now.isBefore(deadline)) {
            return false;
        }

        long id = trip.getId();
        // Un historique insuffisant demande une vérification, sans créer une dette supposée.
        if (reservations.hasUnverifiableJ1History(id, deadline)) {
            if (!trip.isVerificationJ1Requise()) {
                trip.requireJ1Verification();
                journal.record(SensitiveOperationJournal.Action.VERIFICATION_J1_REQUISE, null,
                        SensitiveOperationJournal.Resource.TRAJET, id);
                log.warn("J1 : historique insuffisant pour le trajet {}, vérification nécessaire", id);
            }
            return false;
        }

        if (reservations.countConfirmedAtJ1(id, deadline) >= BookingPolicy.MAX_CONFIRMED_PASSENGERS) {
            trip.recordJ1Check(now);
            journal.record(SensitiveOperationJournal.Action.CONTROLE_J1_REUSSI, null,
                    SensitiveOperationJournal.Resource.TRAJET, id);
            return false;
        }

        // Trajet privé incomplet à l'échéance : conversion en public et dette unique de l'organisateur.
        trip.processJ1(now);
        journal.record(SensitiveOperationJournal.Action.TRAJET_CONVERTI_PUBLIC, null,
                SensitiveOperationJournal.Resource.TRAJET, id);
        if (!debts.existsByTrajetId(id)) {
            var debt = debts.saveAndFlush(new DetteTrajet(trip));
            journal.record(SensitiveOperationJournal.Action.DETTE_CREEE, null,
                    SensitiveOperationJournal.Resource.DETTE, debt.getId());
        }
        return true;
    }
}
