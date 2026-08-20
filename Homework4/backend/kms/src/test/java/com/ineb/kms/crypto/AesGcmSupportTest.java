package com.ineb.kms.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AesGcmSupportTest {

    private static final byte[] SALT = new byte[]{
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    private byte[] deriveKey(String passphrase) {
        return Pbkdf2Support.deriveKey(
                passphrase.toCharArray(), SALT,
                CryptoConstants.PBKDF2_ITERATIONS,
                CryptoConstants.MASTER_KEY_LENGTH_BYTES);
    }

    private byte[] computeKcv(byte[] key) {
        return AesGcmSupport.encrypt(
                key,
                CryptoConstants.kcvFixedIv(),
                CryptoConstants.KCV_PLAIN_TEXT.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("암호화한 데이터를 같은 키와 IV로 복호화하면 원문이 복원된다")
    void encryptDecryptRoundTrip() {
        byte[] key = deriveKey("correct-master-passphrase-over-20");
        byte[] iv = new byte[CryptoConstants.GCM_IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(iv);
        byte[] plain = "개인정보 샘플 데이터 010-1234-5678".getBytes(StandardCharsets.UTF_8);

        byte[] cipherText = AesGcmSupport.encrypt(key, iv, plain);
        byte[] decrypted = AesGcmSupport.decrypt(key, iv, cipherText);

        assertArrayEquals(plain, decrypted);
    }

    @Test
    @DisplayName("KCV: 같은 패스프레이즈로 유도한 키는 저장된 KCV와 일치한다")
    void kcvMatchesForSamePassphrase() {
        byte[] storedKcv = computeKcv(deriveKey("correct-master-passphrase-over-20"));
        byte[] actualKcv = computeKcv(deriveKey("correct-master-passphrase-over-20"));

        assertTrue(MessageDigest.isEqual(storedKcv, actualKcv));
    }

    @Test
    @DisplayName("KCV: 한 글자 틀린 패스프레이즈로 유도한 키는 KCV 불일치로 탐지된다")
    void kcvDetectsWrongPassphrase() {
        byte[] storedKcv = computeKcv(deriveKey("correct-master-passphrase-over-20"));
        byte[] wrongKcv = computeKcv(deriveKey("correct-master-passphrase-over-2O"));

        assertFalse(MessageDigest.isEqual(storedKcv, wrongKcv));
    }
}
