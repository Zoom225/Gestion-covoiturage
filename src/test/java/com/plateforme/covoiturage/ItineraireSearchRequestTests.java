package com.plateforme.covoiturage;

import com.plateforme.covoiturage.dto.request.ItineraireSearchRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ItineraireSearchRequestTests {
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","\t\u2003"})
    void absentOrBlankFiltersDoNotRestrictTheCatalogue(String value) {
        var search=new ItineraireSearchRequest(value,value);
        assertThat(search.origine()).isEmpty();
        assertThat(search.destination()).isEmpty();
    }

    @Test void normalizationPreservesAccentsAndTheMeaningOfTheSearch() {
        var search=new ItineraireSearchRequest("  Bruxelles\u2003","\tLiège ");
        assertThat(search.origine()).isEqualTo("Bruxelles");
        assertThat(search.destination()).isEqualTo("Liège");
    }

    @Test void overlyLongFiltersAreRejectedWithoutSilentTruncation() {
        var search=new ItineraireSearchRequest("a".repeat(256),"b".repeat(256));
        assertThat(search.origine()).hasSize(256);
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var errors=factory.getValidator().validate(search);
            assertThat(errors).hasSize(2);
            assertThat(errors).allSatisfy(error -> assertThat(error.getMessage()).contains("255","caractères"));
            assertThat(factory.getValidator().validate(new ItineraireSearchRequest("a".repeat(255),"b".repeat(255))))
                    .isEmpty();
        }
    }
}
