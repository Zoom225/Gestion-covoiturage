package com.plateforme.covoiturage;

import com.plateforme.covoiturage.dto.request.*;
import com.plateforme.covoiturage.enums.CategorieClient;
import com.plateforme.covoiturage.enums.TypeTrajet;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FrenchValidationMessagesTests {
    static ValidatorFactory factory;
    static Validator validator;

    @BeforeAll static void forceEnglishInterpolationWithoutChangingTheGlobalLocale() {
        var configuration = Validation.byDefaultProvider().configure();
        var delegate = configuration.getDefaultMessageInterpolator();
        factory = configuration.messageInterpolator(new MessageInterpolator() {
            @Override public String interpolate(String message, Context context) {
                return delegate.interpolate(message, context, Locale.ENGLISH);
            }
            @Override public String interpolate(String message, Context context, Locale locale) {
                return delegate.interpolate(message, context, Locale.ENGLISH);
            }
        }).buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll static void closeValidator() { factory.close(); }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(new CreateVehiculeRequest(null), "zoneId", "NotNull", "Ce champ est obligatoire."),
                Arguments.of(new LoginRequest(" ", "mot-de-passe"), "identifiant", "NotBlank", "Ce champ ne doit pas être vide."),
                Arguments.of(new PrivateParticipantRequest(0L), "clientId", "Positive", "La valeur doit être strictement positive."),
                Arguments.of(new ReferencePageRequest(-1, 20), "page", "Min", "La valeur doit être supérieure ou égale à 0."),
                Arguments.of(new ReferencePageRequest(0, 101), "size", "Max", "La valeur doit être inférieure ou égale à 100."),
                Arguments.of(new LoginRequest("a".repeat(101), "mot-de-passe"), "identifiant", "Size",
                        "La longueur doit être comprise entre 0 et 100 caractères."),
                Arguments.of(new AcceptInvitationRequest("format-invalide", "mot-de-passe-de-test"), "jeton", "Pattern",
                        "Le format de ce champ est invalide."),
                Arguments.of(new CreateClientRequest("adresse-invalide", CategorieClient.LOCAL, 1L), "email", "Email",
                        "L'adresse électronique doit être valide."),
                Arguments.of(new DebtPaymentRequest(BigDecimal.ZERO), "montant", "DecimalMin",
                        "La valeur doit être strictement supérieure à 0."),
                Arguments.of(new DebtPaymentRequest(new BigDecimal("81")), "montant", "DecimalMax",
                        "La valeur doit être inférieure ou égale à 80.00."),
                Arguments.of(new DebtPaymentRequest(new BigDecimal("1.111")), "montant", "Digits",
                        "Le nombre doit contenir au plus 2 chiffres avant la virgule et 2 après."),
                Arguments.of(new CreateTrajetRequest(1L, 1L, Instant.parse("2027-01-15T10:00:00Z"),
                        Instant.parse("2027-01-15T09:00:00Z"), TypeTrajet.PUBLIC, "Gare A", "Gare B"),
                        "intervalValid", "AssertTrue", "L'arrivée doit être strictement postérieure au départ.")
        );
    }

    @ParameterizedTest @MethodSource("invalidRequests")
    void userMessagesStayFrenchEvenWithAnEnglishLocale(Object request, String field, String constraint, String expected) {
        var errors = validator.validate(request).stream()
                .filter(error -> error.getPropertyPath().toString().equals(field))
                .filter(error -> error.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName().equals(constraint))
                .toList();
        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().getMessage()).isEqualTo(expected);
    }
}
