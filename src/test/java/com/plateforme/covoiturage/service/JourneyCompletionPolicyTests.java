package com.plateforme.covoiturage.service;

import com.plateforme.covoiturage.entity.Client;
import com.plateforme.covoiturage.entity.Trajet;
import com.plateforme.covoiturage.entity.Vehicule;
import com.plateforme.covoiturage.entity.Zone;
import com.plateforme.covoiturage.enums.CategorieClient;
import com.plateforme.covoiturage.enums.StatutTrajet;
import com.plateforme.covoiturage.enums.TypeTrajet;
import com.plateforme.covoiturage.exception.BusinessRuleException;
import com.plateforme.covoiturage.repository.DetteTrajetRepository;
import com.plateforme.covoiturage.repository.ReservationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class JourneyCompletionPolicyTests {
    private static final Instant ARRIVAL=Instant.parse("2027-03-28T01:30:00Z");
    @Mock ZoneAccessPolicy zones;
    @Mock DetteTrajetRepository debts;
    @Mock ReservationRepository reservations;

    private BookingPolicy policy(Instant now,String clockZone) {
        return new BookingPolicy(zones,debts,reservations,Clock.fixed(now,ZoneId.of(clockZone)));
    }

    private Trajet trip(TypeTrajet type,StatutTrajet status) {
        var zone=new Zone("Zone de test");
        zone.configure("Zone de test","Europe/Brussels",true);
        var organizer=new Client("C000001",CategorieClient.LOCAL,true,zone);
        var trip=new Trajet(zone,new Vehicule(zone),organizer,ARRIVAL.minusSeconds(3600),ARRIVAL,type,"A","B");
        ReflectionTestUtils.setField(trip,"statut",status);
        return trip;
    }

    private void assertTooEarly(BookingPolicy policy,Trajet trip) {
        assertThatThrownBy(() -> policy.requireAfterArrivalForCompletion(trip))
                .isInstanceOfSatisfying(BusinessRuleException.class,error -> {
                    assertThat(error.getCode()).isEqualTo("TRIP_ARRIVAL_NOT_PASSED");
                    assertThat(error.getMessage()).isEqualTo("Opération incompatible avec les règles métier.");
                });
    }

    private void unchanged(Trajet trip,TypeTrajet type,StatutTrajet status) {
        assertThat(trip.getType()).isEqualTo(type);
        assertThat(trip.getStatut()).isEqualTo(status);
        assertThat(trip.getDepart()).isEqualTo(ARRIVAL.minusSeconds(3600));
        assertThat(trip.getArrivee()).isEqualTo(ARRIVAL);
        assertThat(trip.getTraiteJ1Le()).isNull();
    }

    @AfterEach void temporalPreparationNeverQueriesOrWritesBusinessRepositories() {
        verifyNoInteractions(zones,debts,reservations);
    }

    @ParameterizedTest @ValueSource(longs={-86_400_000_000_000L,-1})
    void rejectsBeforeArrivalWithoutMutation(long offsetNanos) {
        var trip=trip(TypeTrajet.PUBLIC,StatutTrajet.PLANIFIE);
        assertTooEarly(policy(ARRIVAL.plusNanos(offsetNanos),"UTC"),trip);
        unchanged(trip,TypeTrajet.PUBLIC,StatutTrajet.PLANIFIE);
    }

    @ParameterizedTest @ValueSource(longs={0,1,1_000_000_000L,86_400_000_000_000L})
    void passingTheTemporalGuardDoesNotCompleteTheTrip(long offsetNanos) {
        var trip=trip(TypeTrajet.PRIVE,StatutTrajet.COMPLET);
        assertThatCode(() -> policy(ARRIVAL.plusNanos(offsetNanos),"UTC").requireAfterArrivalForCompletion(trip))
                .doesNotThrowAnyException();
        unchanged(trip,TypeTrajet.PRIVE,StatutTrajet.COMPLET);
    }

    @ParameterizedTest @CsvSource({"PUBLIC,PLANIFIE","PUBLIC,COMPLET","PUBLIC,ANNULE","PRIVE,PLANIFIE","PRIVE,COMPLET","PRIVE,ANNULE"})
    void temporalGuardDoesNotSelectOrAuthorizeAnySourceStatus(TypeTrajet type,StatutTrajet status) {
        var trip=trip(type,status);
        assertThatCode(() -> policy(ARRIVAL.plusNanos(1),"UTC").requireAfterArrivalForCompletion(trip))
                .doesNotThrowAnyException();
        unchanged(trip,type,status);
    }

    @ParameterizedTest @CsvSource({
            "2027-03-28T03:30:00+02:00,Europe/Brussels",
            "2027-03-27T15:30:00-10:00,Pacific/Honolulu",
            "2027-03-28T10:30:00+09:00,Asia/Tokyo"
    })
    void comparesUtcInstantsAcrossOffsetsAndTheDaylightSavingBoundary(String recordedArrival,String clockZone) {
        Instant sameArrival=OffsetDateTime.parse(recordedArrival).toInstant();
        assertThat(sameArrival).isEqualTo(ARRIVAL);
        var trip=trip(TypeTrajet.PUBLIC,StatutTrajet.PLANIFIE);
        trip.getZone().configure("Autre affichage",clockZone,true);
        assertTooEarly(policy(sameArrival.minusNanos(1),clockZone),trip);
        assertThatCode(() -> policy(sameArrival,clockZone).requireAfterArrivalForCompletion(trip))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy(sameArrival.plusNanos(1),clockZone).requireAfterArrivalForCompletion(trip))
                .doesNotThrowAnyException();
        unchanged(trip,TypeTrajet.PUBLIC,StatutTrajet.PLANIFIE);
    }
}
