package com.plateforme.covoiturage;

import com.plateforme.covoiturage.entity.Client;
import com.plateforme.covoiturage.entity.DetteTrajet;
import com.plateforme.covoiturage.entity.Trajet;
import com.plateforme.covoiturage.entity.Vehicule;
import com.plateforme.covoiturage.entity.Zone;
import com.plateforme.covoiturage.entity.enums.CategorieClient;
import com.plateforme.covoiturage.entity.enums.TypeTrajet;
import com.plateforme.covoiturage.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebtSettlementTests {
    private DetteTrajet debt() {
        var zone = new Zone("Zone de test");
        var organizer = new Client("C0001", CategorieClient.LOCAL, true, zone);
        var departure = Instant.parse("2027-01-15T10:00:00Z");
        return new DetteTrajet(new Trajet(zone, new Vehicule(zone), organizer,
                departure, departure.plusSeconds(3600), TypeTrajet.PRIVE, "Départ", "Arrivée"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "0.001", "20.000", "80.01"})
    void invalidPaymentLeavesDebtUnchanged(String value) {
        var debt = debt();
        BigDecimal amount = value == null ? null : new BigDecimal(value);

        assertThatThrownBy(() -> debt.settle(amount))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INVALID_DEBT_AMOUNT"));
        assertThat(debt.getRegle()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(debt.getStatut()).isEqualTo("ACTIVE");
    }

    @Test
    void partialPaymentsKeepDebtActiveUntilTheFullAmountIsPaid() {
        var debt = debt();
        debt.settle(new BigDecimal("20.50"));
        assertThat(debt.getStatut()).isEqualTo("ACTIVE");
        assertThat(debt.getRegle()).isEqualByComparingTo("20.50");

        debt.settle(new BigDecimal("59.50"));
        assertThat(debt.getRegle()).isEqualByComparingTo("80.00");
        assertThat(debt.getStatut()).isEqualTo("REGULARISEE");
    }

    @Test
    void paymentCannotExceedTheRemainingBalance() {
        var debt = debt();
        debt.settle(new BigDecimal("60"));

        assertThatThrownBy(() -> debt.settle(new BigDecimal("20.01")))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(debt.getRegle()).isEqualByComparingTo("60");
        assertThat(debt.getStatut()).isEqualTo("ACTIVE");
    }
}
