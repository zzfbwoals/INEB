package com.ineb.kms.crypto;

import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 개인정보(연락처·이메일) 컬럼 암호화·검색 해시 코덱.
 * <p>
 * - 암호화: 마스터키 AES-256-GCM, 필드마다 새 랜덤 IV → base64(iv|ct+tag) 로 IV 를 암호문에 동봉
 *   (WrappedSecretStore 와 같은 봉투 패턴 — 별도 iv 컬럼 없음, 2026-09-01 설계 개정).
 * - 검색 해시: 무결성 HMAC 키(integrityKey)로 HMAC-SHA-256, 정규화(연락처 숫자만·이메일 소문자) 후 계산.
 *   정규화가 흔들리면 정확검색이 깨지므로 이 클래스 밖에서 해시를 만들지 않는다.
 */
@Component
public class PersonalDataCodec {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final int IV_LEN = CryptoConstants.GCM_IV_LENGTH_BYTES;

    private final MasterKeyHolder masterKeyHolder;
    private final WrappedSecretStore secretStore;
    private final SecureRandom random = new SecureRandom();

    public PersonalDataCodec(MasterKeyHolder masterKeyHolder, WrappedSecretStore secretStore) {
        this.masterKeyHolder = masterKeyHolder;
        this.secretStore = secretStore;
    }

    public String encrypt(String plain) {
        byte[] iv = new byte[IV_LEN];
        random.nextBytes(iv);
        byte[] ct = AesGcmSupport.encrypt(masterKeyHolder.getKey(), iv, plain.getBytes(StandardCharsets.UTF_8));
        byte[] blob = new byte[IV_LEN + ct.length];
        System.arraycopy(iv, 0, blob, 0, IV_LEN);
        System.arraycopy(ct, 0, blob, IV_LEN, ct.length);
        return Base64.getEncoder().encodeToString(blob);
    }

    /** 복호화 실패(암호문 변조·손상 = GCM 태그 불일치)는 USER_DATA_CORRUPTED(409) */
    public String decrypt(String encoded) {
        try {
            byte[] blob = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(blob, 0, IV_LEN);
            byte[] ct = Arrays.copyOfRange(blob, IV_LEN, blob.length);
            return new String(AesGcmSupport.decrypt(masterKeyHolder.getKey(), iv, ct), StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.USER_DATA_CORRUPTED);
        }
    }

    public String phoneHash(String phone) {
        return hmac(normalizePhone(phone));
    }

    public String emailHash(String email) {
        return hmac(normalizeEmail(email));
    }

    /** 하이픈·공백 유무와 무관하게 같은 해시가 나오도록 숫자만 남긴다 */
    static String normalizePhone(String phone) {
        return phone.replaceAll("\\D", "");
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String hmac(String normalized) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secretStore.integrityKey(), HMAC_ALGO));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("검색 해시 계산에 실패했습니다", e);
        }
    }
}
