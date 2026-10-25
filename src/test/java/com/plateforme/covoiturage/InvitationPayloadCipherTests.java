package com.plateforme.covoiturage;

import com.plateforme.covoiturage.delivery.*;
import com.plateforme.covoiturage.exception.DeliveryUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class InvitationPayloadCipherTests {
    private static String key() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
    private static DeliveryProperties properties(String key) {
        return new DeliveryProperties("test-key", key, null, null, Duration.ofMinutes(1));
    }

    @Test
    void roundTripAndNonceUniquenessWithoutSecretToString() {
        var props = properties(key());
        var cipher = new InvitationPayloadCipher(props);
        String raw = UUID.randomUUID().toString();
        var seen = new HashSet<String>();
        for (int n=0; n<64; n++) {
            UUID id = UUID.randomUUID();
            var encrypted = cipher.encrypt(id, 10, "fiction@delivery.test", raw);
            assertThat(seen.add(Base64.getEncoder().encodeToString(encrypted.nonce()))).isTrue();
            var clear = cipher.decrypt(id, 10, encrypted.keyId(), encrypted.nonce(), encrypted.ciphertext());
            assertThat(raw.equals(clear.token())).isTrue();
            assertThat(clear.recipient()).isEqualTo("fiction@delivery.test");
            assertThat(clear.toString().contains(raw)).isFalse();
            assertThat(encrypted.toString().contains(raw)).isFalse();
        }
        assertThat(props.toString().contains(props.encryptionKey())).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"message", "invitation", "key-id", "nonce", "ciphertext", "wrong-key"})
    void authenticatedEncryptionRejectsTampering(String scenario) {
        var cipher = new InvitationPayloadCipher(properties(key()));
        UUID id = UUID.randomUUID();
        var encrypted = cipher.encrypt(id, 10, "fiction@delivery.test", UUID.randomUUID().toString());
        UUID target = scenario.equals("message") ? UUID.randomUUID() : id;
        long invitation = scenario.equals("invitation") ? 11 : 10;
        String keyId = scenario.equals("key-id") ? "other" : encrypted.keyId();
        byte[] nonce = encrypted.nonce().clone();
        byte[] data = encrypted.ciphertext().clone();
        if (scenario.equals("nonce")) nonce[0] ^= 1;
        if (scenario.equals("ciphertext")) data[data.length-1] ^= 1;
        var decoder = scenario.equals("wrong-key") ? new InvitationPayloadCipher(properties(key())) : cipher;
        assertThatThrownBy(() -> decoder.decrypt(target, invitation, keyId, nonce, data))
                .isInstanceOf(DeliveryUnavailableException.class).hasMessage("Livraison d'invitation indisponible");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-base64", "AA=="})
    void missingOrInvalidKeysFailClosed(String value) {
        var cipher = new InvitationPayloadCipher(properties(value));
        assertThatThrownBy(() -> cipher.encrypt(UUID.randomUUID(), 10, "fiction@delivery.test", "fiction"))
                .isInstanceOf(DeliveryUnavailableException.class);
    }

    @Test
    void nullKeyAndNonPositiveRetryDelayAreRejectedSafely() {
        var cipher = new InvitationPayloadCipher(properties(null));
        assertThatThrownBy(() -> cipher.encrypt(UUID.randomUUID(), 10, "fiction@delivery.test", "fiction"))
                .isInstanceOf(DeliveryUnavailableException.class);
        assertThatThrownBy(() -> new DeliveryProperties(null,null,null,null,Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
