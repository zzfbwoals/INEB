package com.ineb.kms.crypto;

/**
 * 마스터키 유도·검증 관련 상수 (설계 문서 8.2~8.3장 확정값).
 */
public final class CryptoConstants {

    /** 마스터 패스프레이즈 주입 환경변수명 — 유일한 보안 환경변수 */
    public static final String MASTER_PASSPHRASE_ENV = "KMS_MASTER_PASSPHRASE";

    /** PBKDF2-HMAC-SHA256 반복 횟수 */
    public static final int PBKDF2_ITERATIONS = 10_000;

    /** 마스터키 길이 (AES-256 = 32바이트) */
    public static final int MASTER_KEY_LENGTH_BYTES = 32;

    /** Salt 길이 */
    public static final int SALT_LENGTH_BYTES = 16;

    /** GCM 권장 IV 길이 */
    public static final int GCM_IV_LENGTH_BYTES = 12;

    /** GCM 인증 태그 길이 (비트) */
    public static final int GCM_TAG_LENGTH_BITS = 128;

    /** KCV 계산용 고정 평문 */
    public static final String KCV_PLAIN_TEXT = "KMS-KCV-V1";

    /** crypto_config 테이블의 config_key 값 — 마스터키 유도·검증 (비밀 아님) */
    public static final String CONFIG_KEY_SALT = "salt";
    public static final String CONFIG_KEY_KCV = "kcv";

    /** crypto_config 테이블의 config_key 값 — 마스터키로 래핑된 내부 비밀키 (WrappedSecretStore) */
    public static final String CONFIG_KEY_JWT_KEY = "jwt_key";
    public static final String CONFIG_KEY_INTEGRITY_KEY = "integrity_key";

    /**
     * KCV 계산 전용 고정 IV (0x00 12바이트).
     * 고정 IV는 KCV의 고정 평문에만 사용하며, 그 외 모든 암호화는 랜덤 IV를 사용한다.
     */
    public static byte[] kcvFixedIv() {
        return new byte[GCM_IV_LENGTH_BYTES];
    }

    private CryptoConstants() {
    }
}
