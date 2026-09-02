package com.ineb.kms.user;

import com.ineb.kms.crypto.WrappedSecretStore;
import com.ineb.kms.domain.AppUser;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * app_user 행 무결성 해시 (HMAC-SHA256, 16진수 64자).
 * 정규화(설계서 10.3): name|password_hash|status|enc_ver — 구분자 '|', null → "".
 * 암호문 컬럼(phone_enc/email_enc)은 정규화 대상이 아니다 — 변조는 복호화 시 GCM 태그 불일치로 잡힌다.
 * 키와 달리 위반 시 자동 정지는 없고 integrityValid 플래그로만 응답한다 (자동 정지는 key_material 전용 규칙).
 */
@Component
public class UserIntegrityHasher {

    private static final String HMAC_ALGO = "HmacSHA256";

    private final WrappedSecretStore secretStore;
    private final byte[] fixedKey;

    @Autowired
    public UserIntegrityHasher(WrappedSecretStore secretStore) {
        this.secretStore = secretStore;
        this.fixedKey = null;
    }

    /** 테스트용 — 무결성 키를 직접 주입 */
    UserIntegrityHasher(byte[] integrityKey) {
        this.secretStore = null;
        this.fixedKey = integrityKey;
    }

    public String hash(AppUser user) {
        return hmac(normalize(user));
    }

    public void rehash(AppUser user) {
        user.applyIntegrityHash(hash(user));
    }

    /** 저장된 해시와 재계산 값을 상수 시간 비교. 해시가 아직 없는 행은 위반으로 보지 않는다. */
    public boolean verify(AppUser user) {
        if (user.getIntegrityHash() == null) {
            return true;
        }
        return MessageDigest.isEqual(
                user.getIntegrityHash().getBytes(StandardCharsets.UTF_8),
                hash(user).getBytes(StandardCharsets.UTF_8));
    }

    static String normalize(AppUser user) {
        return String.join("|",
                nz(user.getName()),
                nz(user.getPasswordHash()),
                user.getStatus() == null ? "" : user.getStatus().name(),
                String.valueOf(user.getEncVer()));
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private String hmac(String normalized) {
        byte[] key = fixedKey != null ? fixedKey : Objects.requireNonNull(secretStore).integrityKey();
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key, HMAC_ALGO));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("무결성 해시 계산에 실패했습니다", e);
        }
    }
}
