package com.ineb.kms.crypto;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 설정 파일 비밀값 봉투 암호화 — ENC(base64(salt|iv|ciphertext+tag)).
 * <p>
 * 마스터 패스프레이즈에서 PBKDF2로 설정 전용 KEK를 유도해 AES-256-GCM으로 감싼다.
 * salt·iv는 비밀이 아니므로 암호문 앞에 동봉한다 — 값마다 다른 salt를 쓰므로
 * 사전 계산 공격이 상각되지 않고, DB(crypto_config)에 의존하지 않아 DataSource 생성 전에도 복호화할 수 있다.
 * 마스터키(crypto_config.salt로 유도)와는 salt가 달라 서로 다른 키가 나온다.
 */
public final class ConfigSecretCodec {

    public static final String PREFIX = "ENC(";
    public static final String SUFFIX = ")";

    private static final int SALT_LEN = CryptoConstants.SALT_LENGTH_BYTES;
    private static final int IV_LEN = CryptoConstants.GCM_IV_LENGTH_BYTES;
    private static final int KEK_LEN = CryptoConstants.MASTER_KEY_LENGTH_BYTES;

    private ConfigSecretCodec() {
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX) && value.endsWith(SUFFIX);
    }

    /** 평문을 암호화해 ENC(...) 문자열로 만든다. 호출자가 passphrase·plain의 zeroize를 책임진다. */
    public static String encrypt(char[] passphrase, byte[] plain) {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LEN];
        byte[] iv = new byte[IV_LEN];
        random.nextBytes(salt);
        random.nextBytes(iv);

        byte[] kek = Pbkdf2Support.deriveKey(passphrase, salt, CryptoConstants.PBKDF2_ITERATIONS, KEK_LEN);
        try {
            byte[] cipherText = AesGcmSupport.encrypt(kek, iv, plain);
            byte[] blob = new byte[SALT_LEN + IV_LEN + cipherText.length];
            System.arraycopy(salt, 0, blob, 0, SALT_LEN);
            System.arraycopy(iv, 0, blob, SALT_LEN, IV_LEN);
            System.arraycopy(cipherText, 0, blob, SALT_LEN + IV_LEN, cipherText.length);
            return PREFIX + Base64.getEncoder().encodeToString(blob) + SUFFIX;
        } finally {
            Arrays.fill(kek, (byte) 0);
        }
    }

    /**
     * ENC(...) 문자열을 복호화한다. 패스프레이즈가 틀리면 GCM 인증 태그 불일치로 예외가 발생한다(fail-fast).
     * 호출자가 반환된 평문의 zeroize를 책임진다.
     */
    public static byte[] decrypt(char[] passphrase, String encoded) {
        if (!isEncrypted(encoded)) {
            throw new MasterKeyException("ENC(...) 형식이 아닌 값은 복호화할 수 없습니다");
        }
        byte[] blob;
        try {
            blob = Base64.getDecoder().decode(
                    encoded.substring(PREFIX.length(), encoded.length() - SUFFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new MasterKeyException("ENC(...) 내부가 올바른 Base64가 아닙니다", e);
        }
        if (blob.length <= SALT_LEN + IV_LEN) {
            throw new MasterKeyException("ENC(...) 암호문 길이가 너무 짧습니다");
        }

        byte[] salt = Arrays.copyOfRange(blob, 0, SALT_LEN);
        byte[] iv = Arrays.copyOfRange(blob, SALT_LEN, SALT_LEN + IV_LEN);
        byte[] cipherText = Arrays.copyOfRange(blob, SALT_LEN + IV_LEN, blob.length);

        byte[] kek = Pbkdf2Support.deriveKey(passphrase, salt, CryptoConstants.PBKDF2_ITERATIONS, KEK_LEN);
        try {
            return AesGcmSupport.decrypt(kek, iv, cipherText);
        } catch (MasterKeyException e) {
            throw new MasterKeyException(
                    "설정 비밀 복호화 실패 — 마스터 패스프레이즈가 올바르지 않거나 암호문이 손상되었습니다", e);
        } finally {
            Arrays.fill(kek, (byte) 0);
        }
    }
}
