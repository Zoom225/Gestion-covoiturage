package com.plateforme.covoiturage.delivery;

import com.plateforme.covoiturage.exception.DeliveryUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

@Component
@EnableConfigurationProperties(DeliveryProperties.class)
public class InvitationPayloadCipher {
    private final DeliveryProperties properties;
    private final SecureRandom random = new SecureRandom();

    public InvitationPayloadCipher(DeliveryProperties properties) { this.properties = properties; }

    public record Encrypted(String keyId, byte[] nonce, byte[] ciphertext) {
        @Override public String toString() { return "Encrypted[REDACTED]"; }
    }
    public record Payload(@JsonIgnore String recipient, @JsonIgnore String token) {
        @Override public String toString() { return "Payload[REDACTED]"; }
    }

    public Encrypted encrypt(UUID messageId, long invitationId, String recipient, String token) {
        byte[] plaintext = null;
        try {
            var buffer = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(buffer)) {
                output.writeUTF(recipient);
                output.writeUTF(token);
            }
            plaintext = buffer.toByteArray();
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            var cipher = cipher(Cipher.ENCRYPT_MODE, properties.keyId(), nonce, messageId, invitationId);
            return new Encrypted(properties.keyId(), nonce, cipher.doFinal(plaintext));
        } catch (Exception ex) {
            throw new DeliveryUnavailableException();
        } finally { if (plaintext != null) Arrays.fill(plaintext, (byte) 0); }
    }

    public Payload decrypt(UUID messageId, long invitationId, String keyId, byte[] nonce, byte[] ciphertext) {
        byte[] plaintext = null;
        try {
            plaintext = cipher(Cipher.DECRYPT_MODE, keyId, nonce, messageId, invitationId).doFinal(ciphertext);
            try (var input = new DataInputStream(new ByteArrayInputStream(plaintext))) {
                String recipient = input.readUTF();
                String token = input.readUTF();
                if (input.available() != 0) throw new IOException();
                return new Payload(recipient, token);
            }
        } catch (Exception ex) {
            throw new DeliveryUnavailableException();
        } finally { if (plaintext != null) Arrays.fill(plaintext, (byte) 0); }
    }

    private Cipher cipher(int mode, String keyId, byte[] nonce, UUID messageId, long invitationId) throws Exception {
        if (keyId == null || !keyId.matches("[A-Za-z0-9_-]{1,64}") || !keyId.equals(properties.keyId())
                || properties.encryptionKey() == null || nonce == null || nonce.length != 12) {
            throw new DeliveryUnavailableException();
        }
        byte[] key = Base64.getDecoder().decode(properties.encryptionKey());
        try {
            if (key.length != 32) throw new DeliveryUnavailableException();
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(("driveconnect-invitation:" + messageId + ":" + invitationId + ":" + keyId)
                    .getBytes(StandardCharsets.UTF_8));
            return cipher;
        } finally { Arrays.fill(key, (byte) 0); }
    }
}
