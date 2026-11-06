package com.farmatodo.challenge.tokenization.infrastructure.out.crypto;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.farmatodo.challenge.tokenization.domain.CardData;
import com.farmatodo.challenge.tokenization.domain.EncryptedCard;
import com.farmatodo.challenge.tokenization.domain.Ports.CardEncryptor;
import com.farmatodo.challenge.tokenization.infrastructure.config.CryptoProperties;

import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * AES-256-GCM card encryption (ADR-004).
 * <ul>
 *   <li><b>Authenticated</b>: the 128-bit tag detects any modification of the ciphertext.</li>
 *   <li><b>Fresh 96-bit IV per encryption</b> from SecureRandom: an IV must never repeat under the same key.</li>
 *   <li><b>AAD = token</b>: the ciphertext only decrypts in its own row; copying it to another card fails.</li>
 *   <li><b>Key id</b> stored with the data, enabling key rotation.</li>
 * </ul>
 * Cipher instances are not thread-safe, so a new one is created per operation (cheap compared with the I/O).
 */
@Component
class AesGcmCardEncryptor implements CardEncryptor {

    static final String TRANSFORMATION = "AES/GCM/NoPadding";
    static final int IV_BYTES = 12;
    static final int TAG_BITS = 128;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final CryptoProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    AesGcmCardEncryptor(CryptoProperties properties) {
        this.properties = properties;
    }

    @Override
    public EncryptedCard encrypt(CardData data, byte[] associatedData) {
        String keyId = properties.activeKeyId();
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, keyId, iv, associatedData);
            byte[] ciphertext = cipher.doFinal(JSON.writeValueAsBytes(data));
            return new EncryptedCard(ciphertext, iv, keyId);
        } catch (GeneralSecurityException e) {
            throw new CardCryptoException("Card encryption failed", e);
        }
    }

    @Override
    public CardData decrypt(EncryptedCard encrypted, byte[] associatedData) {
        try {
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, encrypted.keyId(), encrypted.iv(), associatedData);
            return JSON.readValue(cipher.doFinal(encrypted.ciphertext()), CardData.class);
        } catch (GeneralSecurityException e) {
            // AEADBadTagException lands here: tampered data, wrong key or wrong AAD
            throw new CardCryptoException("Card decryption failed (data tampered or wrong key)", e);
        }
    }

    private Cipher cipher(int mode, String keyId, byte[] iv, byte[] associatedData) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(mode, new SecretKeySpec(properties.keyBytes(keyId), "AES"), new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(associatedData);
        return cipher;
    }

    /** Unchecked wrapper: crypto failures are server errors (500), never details for the client. */
    static class CardCryptoException extends RuntimeException {
        CardCryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
