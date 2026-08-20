package com.ineb.kms.crypto;

import java.security.GeneralSecurityException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2-HMAC-SHA256 키 유도 유틸.
 */
public final class Pbkdf2Support {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private Pbkdf2Support() {
    }

    /**
     * 패스프레이즈와 salt로부터 키를 유도한다.
     * 호출자가 passphrase 배열의 zeroize를 책임진다 (내부 KeySpec 사본은 여기서 파기).
     */
    public static byte[] deriveKey(char[] passphrase, byte[] salt, int iterations, int keyLengthBytes) {
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, iterations, keyLengthBytes * 8);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new MasterKeyException("PBKDF2 키 유도에 실패했습니다", e);
        } finally {
            spec.clearPassword();
        }
    }
}
