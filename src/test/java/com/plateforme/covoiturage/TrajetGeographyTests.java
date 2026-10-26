package com.plateforme.covoiturage;

import com.plateforme.covoiturage.entity.Client;
import com.plateforme.covoiturage.entity.Trajet;
import com.plateforme.covoiturage.entity.Vehicule;
import com.plateforme.covoiturage.entity.Zone;
import com.plateforme.covoiturage.enums.CategorieClient;
import com.plateforme.covoiturage.enums.TypeTrajet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class TrajetGeographyTests {
    Trajet trip(String origine, String destination) {
        var zone = new Zone("Zone de test");
        var client = new Client("C0001", CategorieClient.LOCAL, true, zone);
        return new Trajet(zone, new Vehicule(zone), client, Instant.parse("2027-01-01T10:00:00Z"),
                Instant.parse("2027-01-01T11:00:00Z"), TypeTrajet.PUBLIC, origine, destination);
    }

    static Stream<Arguments> invalidLocations() {
        return Stream.of(null, "", "   ", "\t\r\n", "\u2003", "x".repeat(256))
                .flatMap(value -> Stream.of(Arguments.of(value, "Gare B"), Arguments.of("Gare A", value)));
    }

    @ParameterizedTest @MethodSource("invalidLocations")
    void newTripsCannotBypassGeographicalValidation(String origine, String destination) {
        assertThatIllegalArgumentException().isThrownBy(() -> trip(origine, destination));
    }

    @Test void locationsAreNormalizedWithoutLosingAccents() {
        var trip = trip("  Gare de Bruxelles-Midi  ", "\u2003École de Liège\u2003");
        assertThat(trip.getOrigine()).isEqualTo("Gare de Bruxelles-Midi");
        assertThat(trip.getDestination()).isEqualTo("École de Liège");
    }

    @Test void technicalLimitDoesNotInventAProhibitionOnIdenticalLabels() {
        String label = "É".repeat(255);
        var trip = trip(" " + label + " ", label);
        assertThat(trip.getOrigine()).isEqualTo(label);
        assertThat(trip.getDestination()).isEqualTo(label);
    }
}
