package com.ineb.kms.crypto;

import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM 암복호화 유틸.
 * KCV 계산 외에 이후 주차의 키 래핑·개인정보·첨부파일 암호화에서도 재사용한다.
 */
public final class AesGcmSupport {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";

    private AesGcmSupport() {
    }

    public static byte[] encrypt(byte[] key, byte[] iv, byte[] plain) {
        return process(Cipher.ENCRYPT_MODE, key, iv, plain);
    }

    public static byte[] decrypt(byte[] key, byte[] iv, byte[] cipherText) {
        return process(Cipher.DECRYPT_MODE, key, iv, cipherText);
    }

    private static byte[] process(int mode, byte[] key, byte[] iv, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(CryptoConstants.GCM_TAG_LENGTH_BITS, iv);
            cipher.init(mode, new SecretKeySpec(key, KEY_ALGORITHM), gcmSpec);
            return cipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new MasterKeyException("AES-GCM 연산에 실패했습니다", e);
        }
    }
}
