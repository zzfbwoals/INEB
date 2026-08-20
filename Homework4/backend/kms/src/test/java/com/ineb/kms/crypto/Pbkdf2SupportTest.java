package com.ineb.kms.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Pbkdf2SupportTest {

    private static final byte[] SALT = new byte[]{
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    private byte[] derive(String passphrase, byte[] salt) {
        return Pbkdf2Support.deriveKey(
                passphrase.toCharArray(), salt,
                CryptoConstants.PBKDF2_ITERATIONS,
                CryptoConstants.MASTER_KEY_LENGTH_BYTES);
    }

    @Test
    @DisplayName("같은 패스프레이즈와 salt로는 항상 같은 32바이트 키가 유도된다")
    void deterministicDerivation() {
        byte[] first = derive("correct-master-passphrase-over-20", SALT);
        byte[] second = derive("correct-master-passphrase-over-20", SALT);

        assertEquals(CryptoConstants.MASTER_KEY_LENGTH_BYTES, first.length);
        assertArrayEquals(first, second);
    }

    @Test
    @DisplayName("패스프레이즈가 한 글자만 달라도 전혀 다른 키가 유도된다")
    void differentPassphraseProducesDifferentKey() {
        byte[] correct = derive("correct-master-passphrase-over-20", SALT);
        byte[] wrong = derive("correct-master-passphrase-over-2O", SALT);

        assertFalse(Arrays.equals(correct, wrong));
    }

    @Test
    @DisplayName("salt가 다르면 같은 패스프레이즈라도 다른 키가 유도된다")
    void differentSaltProducesDifferentKey() {
        byte[] otherSalt = SALT.clone();
        otherSalt[0] ^= (byte) 0xFF;

        byte[] first = derive("correct-master-passphrase-over-20", SALT);
        byte[] second = derive("correct-master-passphrase-over-20", otherSalt);

        assertFalse(Arrays.equals(first, second));
    }
}
