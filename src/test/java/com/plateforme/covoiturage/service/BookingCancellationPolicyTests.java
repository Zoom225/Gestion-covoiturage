package com.plateforme.covoiturage.service;

import com.plateforme.covoiturage.entity.Client;
import com.plateforme.covoiturage.entity.Trajet;
import com.plateforme.covoiturage.entity.Vehicule;
import com.plateforme.covoiturage.entity.Zone;
import com.plateforme.covoiturage.enums.CategorieClient;
import com.plateforme.covoiturage.enums.StatutReservation;
import com.plateforme.covoiturage.enums.StatutTrajet;
import com.plateforme.covoiturage.enums.TypeTrajet;
import com.plateforme.covoiturage.exception.BusinessRuleException;
import com.plateforme.covoiturage.repository.DetteTrajetRepository;
import com.plateforme.covoiturage.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingCancellationPolicyTests {
    private static final long TRIP_ID = 42L;
    private static final Instant DEPARTURE = Instant.parse("2027-01-15T10:00:00Z");
    private static final Instant DEADLINE = Instant.parse("2027-01-13T23:15:00Z");
    @Mock ZoneAccessPolicy zones;
    @Mock DetteTrajetRepository debts;
    @Mock ReservationRepository reservations;

    private BookingPolicy policy(Instant now) {
        return new BookingPolicy(zones, debts, reservations, Clock.fixed(now, ZoneOffset.UTC));
    }

    private Trajet trip(TypeTrajet type, StatutTrajet status, Instant departure) {
        var zone = new Zone("Zone de test");
        zone.configure("Zone de test", "Europe/Brussels", true);
        var organizer = new Client("C000001", CategorieClient.LOCAL, true, zone);
        var trip = new Trajet(zone, new Vehicule(zone), organizer, departure,
                departure.plusSeconds(3600), type, "Départ", "Arrivée");
        ReflectionTestUtils.setField(trip, "id", TRIP_ID);
        ReflectionTestUtils.setField(trip, "statut", status);
        return trip;
    }

    private Trajet privateTrip() {
        return trip(TypeTrajet.PRIVE, StatutTrajet.PLANIFIE, DEPARTURE);
    }

    private void assertBlocked(BookingPolicy policy, Trajet trip) {
        assertThatThrownBy(() -> policy.requireCancellationPreservesJ1Debt(trip))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        error -> assertThat(error.getCode()).isEqualTo("PRIVATE_CANCELLATION_AVOIDS_J1_DEBT"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 2, 3})
    void blocksIncompletePrivateTripBeforeJ1WithoutChangingAnything(long confirmed) {
        var trip = privateTrip();
        var organizer = trip.getOrganisateur();
        var vehicle = trip.getVehicule();
        when(reservations.countByTrajetIdAndStatut(TRIP_ID, StatutReservation.CONFIRMEE)).thenReturn(confirmed);

        assertBlocked(policy(DEADLINE.minusNanos(1)), trip);

        assertThat(trip.getStatut()).isEqualTo(StatutTrajet.PLANIFIE);
        assertThat(trip.getType()).isEqualTo(TypeTrajet.PRIVE);
        assertThat(trip.getDepart()).isEqualTo(DEPARTURE);
        assertThat(trip.getArrivee()).isEqualTo(DEPARTURE.plusSeconds(3600));
        assertThat(trip.getOrganisateur()).isSameAs(organizer);
        assertThat(trip.getVehicule()).isSameAs(vehicle);
        assertThat(trip.getTraiteJ1Le()).isNull();
        verify(reservations).countByTrajetIdAndStatut(TRIP_ID, StatutReservation.CONFIRMEE);
        verify(debts).existsByTrajetId(TRIP_ID);
        verifyNoMoreInteractions(reservations, debts);
        verifyNoInteractions(zones);
    }

    @Test
    void fourConfirmedParticipantsIncludingOrganizerDoNotTriggerThisGuard() {
        when(reservations.countByTrajetIdAndStatut(TRIP_ID, StatutReservation.CONFIRMEE)).thenReturn(4L);

        assertThatCode(() -> policy(DEADLINE.minusNanos(1)).requireCancellationPreservesJ1Debt(privateTrip()))
                .doesNotThrowAnyException();

        verify(reservations).countByTrajetIdAndStatut(TRIP_ID, StatutReservation.CONFIRMEE);
        verifyNoMoreInteractions(reservations);
        verifyNoInteractions(debts, zones);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 86_400_000_000_000L})
    void guardDoesNotDefineCancellationRightsAtOrAfterJ1(long nanosAfterDeadline) {
        assertThatCode(() -> policy(DEADLINE.plusNanos(nanosAfterDeadline))
                .requireCancellationPreservesJ1Debt(privateTrip())).doesNotThrowAnyException();
        verifyNoInteractions(reservations, debts, zones);
    }

    @ParameterizedTest
    @CsvSource({"PUBLIC,PLANIFIE", "PUBLIC,COMPLET", "PUBLIC,ANNULE", "PRIVE,COMPLET", "PRIVE,ANNULE"})
    void otherTypesAndStatusesDoNotAcquireAnAdditionalCancellationRule(TypeTrajet type, StatutTrajet status) {
        var trip = trip(type, status, DEPARTURE);

        assertThatCode(() -> policy(DEADLINE.minusNanos(1)).requireCancellationPreservesJ1Debt(trip))
                .doesNotThrowAnyException();

        assertThat(trip.getType()).isEqualTo(type);
        assertThat(trip.getStatut()).isEqualTo(status);
        verifyNoInteractions(reservations, debts, zones);
    }

    @Test
    void existingDebtRegardlessOfSettlementDoesNotRequireAnotherDebt() {
        when(debts.existsByTrajetId(TRIP_ID)).thenReturn(true);

        assertThatCode(() -> policy(DEADLINE.minusNanos(1)).requireCancellationPreservesJ1Debt(privateTrip()))
                .doesNotThrowAnyException();

        verify(reservations).countByTrajetIdAndStatut(TRIP_ID, StatutReservation.CONFIRMEE);
        verify(debts).existsByTrajetId(TRIP_ID);
        verifyNoMoreInteractions(reservations, debts);
        verifyNoInteractions(zones);
    }

    @ParameterizedTest
    @CsvSource({
            "2027-01-15T10:00:00Z,2027-01-13T23:15:00Z",
            "2027-07-15T10:00:00Z,2027-07-13T22:15:00Z",
            "2027-03-29T10:00:00Z,2027-03-27T23:15:00Z",
            "2027-10-25T10:00:00Z,2027-10-23T22:15:00Z",
            "2027-07-14T22:30:00Z,2027-07-13T22:15:00Z"
    })
    void usesExistingBrusselsCalendarDeadlineIncludingDstAndLocalDate(String departure, String boundary) {
        var trip = trip(TypeTrajet.PRIVE, StatutTrajet.PLANIFIE, Instant.parse(departure));
        var deadline = Instant.parse(boundary);

        assertBlocked(policy(deadline.minusNanos(1)), trip);
        assertThatCode(() -> policy(deadline).requireCancellationPreservesJ1Debt(trip)).doesNotThrowAnyException();

        verify(reservations).countByTrajetIdAndStatut(TRIP_ID, StatutReservation.CONFIRMEE);
        verify(debts).existsByTrajetId(TRIP_ID);
        verifyNoMoreInteractions(reservations, debts);
    }

    @Test
    void j1UsesBusinessTimezoneRatherThanTheTripsZoneTimezone() {
        var trip = privateTrip();
        trip.getZone().configure("Autre fuseau", "Pacific/Honolulu", true);

        assertBlocked(policy(DEADLINE.minusNanos(1)), trip);
        assertThatCode(() -> policy(DEADLINE).requireCancellationPreservesJ1Debt(trip)).doesNotThrowAnyException();
    }
}
