package com.plateforme.covoiturage;

import com.plateforme.covoiturage.entity.Vehicule;
import com.plateforme.covoiturage.enums.StatutReservation;
import com.plateforme.covoiturage.enums.TypeTrajet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class TrajetGeographyIntegrationTests extends JourneyIntegrationSupport {
    static Stream<Arguments> invalidLocations() {
        return Stream.of("origine", "destination").flatMap(field ->
                Stream.of("missing", "null", "empty", "spaces", "unicode", "tooLong")
                        .map(kind -> Arguments.of(field, kind)));
    }

    @ParameterizedTest @MethodSource("invalidLocations")
    void invalidLocationsReturn400WithoutCreatingTripOrBooking(String field, String kind) throws Exception {
        var input = new HashMap<>(request(NOW.plusSeconds(172800), NOW.plusSeconds(176400), TypeTrajet.PUBLIC));
        switch (kind) {
            case "missing" -> input.remove(field);
            case "null" -> input.put(field, null);
            case "empty" -> input.put(field, "");
            case "spaces" -> input.put(field, " \t\r\n ");
            case "unicode" -> input.put(field, "\u2003");
            default -> input.put(field, "x".repeat(256));
        }
        var response = call("POST", "/api/trajets", local, input);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(read(response).path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(trips.count()).isZero();
        assertThat(reservations.count()).isZero();
    }

    @ParameterizedTest @EnumSource(TypeTrajet.class)
    void createListAndDetailReturnPersistedLocationsSeparatelyFromTimes(TypeTrajet type) throws Exception {
        var input = new HashMap<>(request(NOW.plusSeconds(172800), NOW.plusSeconds(176400), type));
        input.put("origine", "  Gare de Bruxelles-Midi  ");
        input.put("destination", "\u2003Gare de Liège-Guillemins\u2003");
        var response = call("POST", "/api/trajets", local, input);
        assertThat(response.statusCode()).isEqualTo(201);
        var created = read(response);
        long id = created.path("trajetId").asLong();
        assertThat(created.path("origine").asText()).isEqualTo("Gare de Bruxelles-Midi");
        assertThat(created.path("destination").asText()).isEqualTo("Gare de Liège-Guillemins");
        assertThat(created.path("depart").asText()).isEqualTo(NOW.plusSeconds(172800).toString());
        assertThat(created.path("arrivee").asText()).isEqualTo(NOW.plusSeconds(176400).toString());
        assertThat(created.path("organisateurId").asLong()).isEqualTo(local.getClient().getId());
        var persisted = trips.findById(id).orElseThrow();
        assertThat(persisted.getOrigine()).isEqualTo("Gare de Bruxelles-Midi");
        assertThat(persisted.getDestination()).isEqualTo("Gare de Liège-Guillemins");
        var detail = call("GET", "/api/trajets/" + id, local, null);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(read(detail).path("destination").asText()).isEqualTo(persisted.getDestination());
        var page = read(call("GET", "/api/trajets?size=1", local, null));
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.path("content").get(0).path("origine").asText()).isEqualTo(persisted.getOrigine());
        assertThat(reservations.countByTrajetIdAndStatut(id, StatutReservation.EN_ATTENTE)).isEqualTo(1);
        assertThat(reservations.countByTrajetIdAndStatut(id, StatutReservation.CONFIRMEE)).isZero();
    }

    @Test void normalizedMaximumLengthAndIdenticalLabelsAreAccepted() throws Exception {
        var input = new HashMap<>(request(NOW.plusSeconds(172800), NOW.plusSeconds(176400), TypeTrajet.PUBLIC));
        String label = "É".repeat(255);
        input.put("origine", "  " + label + "  ");
        input.put("destination", label);
        var response = call("POST", "/api/trajets", local, input);
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(read(response).path("origine").asText()).isEqualTo(label);
        assertThat(read(response).path("destination").asText()).isEqualTo(label);
    }

    @Test void locationsRemainInsideExistingZoneAndPrivateVisibilityScopes() throws Exception {
        var privateInput = new HashMap<>(request(NOW.plusSeconds(172800), NOW.plusSeconds(176400), TypeTrajet.PRIVE));
        privateInput.put("origine", "Point privé de départ");
        privateInput.put("destination", "Point privé de destination");
        var privateResponse = call("POST", "/api/trajets", local, privateInput);
        assertThat(privateResponse.statusCode()).isEqualTo(201);
        long privateId = read(privateResponse).path("trajetId").asLong();

        var awayVehicle = vehicles.saveAndFlush(new Vehicule(away));
        var awayInput = new HashMap<>(request(NOW.plusSeconds(172800), NOW.plusSeconds(176400), TypeTrajet.PUBLIC));
        awayInput.put("zoneId", away.getId());
        awayInput.put("vehiculeId", awayVehicle.getId());
        awayInput.put("origine", "Point hors zone");
        assertThat(call("POST", "/api/trajets", otherLocal, awayInput).statusCode()).isEqualTo(201);

        var localPage = call("GET", "/api/trajets?size=1", local, null);
        assertThat(read(localPage).path("totalElements").asLong()).isEqualTo(1);
        assertThat(localPage.body()).doesNotContain("Point hors zone");
        var vipPage = call("GET", "/api/trajets?size=1", vip, null);
        assertThat(read(vipPage).path("totalElements").asLong()).isZero();
        assertThat(vipPage.body()).doesNotContain("Point privé", "Point hors zone");
        var hidden = call("GET", "/api/trajets/" + privateId, vip, null);
        assertThat(hidden.statusCode()).isEqualTo(404);
        assertThat(hidden.body()).doesNotContain("Point privé");
        assertThat(read(call("GET", "/api/trajets?zoneId=" + away.getId(), local, null))
                .path("totalElements").asLong()).isZero();

        assertThat(call("POST", "/api/trajets/" + privateId + "/participants", local,
                Map.of("clientId", vip.getClient().getId())).statusCode()).isEqualTo(201);
        assertThat(read(call("GET", "/api/trajets/" + privateId, vip, null))
                .path("origine").asText()).isEqualTo("Point privé de départ");
    }

    @ParameterizedTest @ValueSource(strings={"anonymous", "admin", "otherZone"})
    void locationsDoNotBypassExistingAuthenticationAndRoleChecks(String kind) throws Exception {
        long id = create(local, TypeTrajet.PUBLIC);
        var actor = switch (kind) { case "admin" -> global; case "otherZone" -> otherLocal; default -> null; };
        var response = call("GET", "/api/trajets/" + id, actor, null);
        assertThat(response.statusCode()).isEqualTo(switch (kind) {
            case "admin" -> 403; case "otherZone" -> 404; default -> 401;
        });
        assertThat(response.body()).doesNotContain("Gare de Bruxelles-Midi", "Gare de Liège-Guillemins");
    }

    @Test void openApiDocumentsLocationsAsRequiredWithoutReplacingTimeFields() throws Exception {
        var response = call("GET", "/v3/api-docs", null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        var schemas = read(response).path("components").path("schemas");
        var request = schemas.path("CreateTrajetRequest");
        assertThat(request.path("required").toString()).contains("\"origine\"", "\"destination\"");
        assertThat(request.path("properties").path("origine").path("maxLength").asInt()).isEqualTo(255);
        assertThat(request.path("properties").path("destination").path("maxLength").asInt()).isEqualTo(255);
        assertThat(request.path("properties").path("depart").path("format").asText()).isEqualTo("date-time");
        assertThat(schemas.path("TrajetResponse").path("properties").has("origine")).isTrue();
        assertThat(schemas.path("TrajetResponse").path("properties").has("destination")).isTrue();
    }
}
