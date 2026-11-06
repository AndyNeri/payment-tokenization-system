package com.farmatodo.challenge.tokenization.infrastructure.out.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import javax.crypto.AEADBadTagException;

import com.farmatodo.challenge.tokenization.domain.CardData;
import com.farmatodo.challenge.tokenization.domain.EncryptedCard;
import com.farmatodo.challenge.tokenization.infrastructure.config.CryptoProperties;

import org.junit.jupiter.api.Test;

class AesGcmCardEncryptorTest {

    static final String KEY_1 = key((byte) 1);
    static final String KEY_2 = key((byte) 2);
    static final CardData DATA = new CardData("4111111111111111", 12, 2029, "Andrés Neri");
    static final byte[] AAD = "tok_abc".getBytes(StandardCharsets.UTF_8);

    final AesGcmCardEncryptor encryptor = new AesGcmCardEncryptor(new CryptoProperties("k1", Map.of("k1", KEY_1)));

    static String key(byte fill) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, fill);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    void roundTrip() {
        EncryptedCard encrypted = encryptor.encrypt(DATA, AAD);

        assertThat(encrypted.keyId()).isEqualTo("k1");
        assertThat(encrypted.iv()).hasSize(AesGcmCardEncryptor.IV_BYTES);
        assertThat(encryptor.decrypt(encrypted, AAD)).isEqualTo(DATA);
    }

    @Test
    void ciphertextDoesNotContainThePanAndCarriesTheTag() {
        EncryptedCard encrypted = encryptor.encrypt(DATA, AAD);

        assertThat(new String(encrypted.ciphertext(), StandardCharsets.ISO_8859_1)).doesNotContain(DATA.pan());
        String json = "{\"pan\":\"4111111111111111\",\"expirationMonth\":12,\"expirationYear\":2029,\"holderName\":\"Andrés Neri\"}";
        int plaintextLength = json.getBytes(StandardCharsets.UTF_8).length;
        assertThat(encrypted.ciphertext()).hasSize(plaintextLength + AesGcmCardEncryptor.TAG_BITS / 8);
    }

    @Test
    void sameDataEncryptsDifferentlyEveryTime() {
        EncryptedCard first = encryptor.encrypt(DATA, AAD);
        EncryptedCard second = encryptor.encrypt(DATA, AAD);

        assertThat(first.iv()).isNotEqualTo(second.iv());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    }

    @Test
    void tamperedCiphertextIsDetected() {
        EncryptedCard encrypted = encryptor.encrypt(DATA, AAD);
        byte[] tampered = encrypted.ciphertext();
        tampered[0] ^= 0x01;

        assertThatThrownBy(() -> encryptor.decrypt(new EncryptedCard(tampered, encrypted.iv(), "k1"), AAD))
                .isInstanceOf(AesGcmCardEncryptor.CardCryptoException.class)
                .hasCauseInstanceOf(AEADBadTagException.class);
    }

    @Test
    void ciphertextMovedToAnotherTokenDoesNotDecrypt() {
        EncryptedCard encrypted = encryptor.encrypt(DATA, AAD);

        assertThatThrownBy(() -> encryptor.decrypt(encrypted, "tok_other".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AesGcmCardEncryptor.CardCryptoException.class);
    }

    @Test
    void keyRotationKeepsOldDataReadable() {
        EncryptedCard oldRow = encryptor.encrypt(DATA, AAD);
        var rotated = new AesGcmCardEncryptor(new CryptoProperties("k2", Map.of("k1", KEY_1, "k2", KEY_2)));

        EncryptedCard newRow = rotated.encrypt(DATA, AAD);

        assertThat(newRow.keyId()).isEqualTo("k2");
        assertThat(rotated.decrypt(oldRow, AAD)).isEqualTo(DATA);
        assertThat(rotated.decrypt(newRow, AAD)).isEqualTo(DATA);
    }

    @Test
    void unknownKeyIdFails() {
        EncryptedCard encrypted = new EncryptedCard(new byte[32], new byte[12], "k9");
        assertThatThrownBy(() -> encryptor.decrypt(encrypted, AAD)).isInstanceOf(IllegalStateException.class);
    }
}
