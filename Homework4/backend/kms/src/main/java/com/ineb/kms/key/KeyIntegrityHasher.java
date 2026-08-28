package com.ineb.kms.key;

import com.ineb.kms.common.KstTime;
import com.ineb.kms.crypto.WrappedSecretStore;
import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.KeyMaterial;
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
 * crypto_key · key_material 행 무결성 해시 (HMAC-SHA256, 16진수 64자).
 * 정규화 규칙(설계서 10.3): 구분자 '|', null → "", 날짜는 KST "yyyy-MM-dd HH:mm:ss".
 *   crypto_key   : key_uid|key_name|algorithm|key_size|mode|purpose|status|current_version|auto_rotate|rotation_period_days
 *   key_material : key_id|version|state|wrapped_key|iv|wrap_algo|activation_date
 * state 가 포함되므로 모든 상태 전이 직후 재계산해야 한다 — 이 규칙이 흔들리면 정상 데이터가 전부 위반으로 판정된다.
 * 무결성 키는 마스터키로 래핑해 crypto_config 에 보관한 독립 난수({@link WrappedSecretStore#integrityKey()}).
 */
@Component
public class KeyIntegrityHasher {

    private static final String HMAC_ALGO = "HmacSHA256";

    private final WrappedSecretStore secretStore;
    private final byte[] fixedKey;

    @Autowired
    public KeyIntegrityHasher(WrappedSecretStore secretStore) {
        this.secretStore = secretStore;
        this.fixedKey = null;
    }

    /** 테스트용 — 무결성 키를 직접 주입 */
    KeyIntegrityHasher(byte[] integrityKey) {
        this.secretStore = null;
        this.fixedKey = integrityKey;
    }

    public String hash(CryptoKey key) {
        return hmac(normalize(key));
    }

    public String hash(KeyMaterial material) {
        return hmac(normalize(material));
    }

    public void rehash(CryptoKey key) {
        key.applyIntegrityHash(hash(key));
    }

    public void rehash(KeyMaterial material) {
        material.applyIntegrityHash(hash(material));
    }

    /** 저장된 해시와 재계산 값을 상수 시간 비교. 해시가 아직 없는 행은 위반으로 보지 않는다. */
    public boolean verify(CryptoKey key) {
        return matches(key.getIntegrityHash(), hash(key));
    }

    public boolean verify(KeyMaterial material) {
        return matches(material.getIntegrityHash(), hash(material));
    }

    static String normalize(CryptoKey key) {
        return String.join("|",
                nz(key.getKeyUid()),
                nz(key.getKeyName()),
                key.getAlgorithm() == null ? "" : key.getAlgorithm().name(),
                String.valueOf(key.getKeySize()),
                key.getMode() == null ? "" : key.getMode().name(),
                key.getPurpose() == null ? "" : key.getPurpose().name(),
                key.getStatus() == null ? "" : key.getStatus().name(),
                String.valueOf(key.getCurrentVersion()),
                String.valueOf(key.isAutoRotate()),
                key.getRotationPeriodDays() == null ? "" : String.valueOf(key.getRotationPeriodDays()));
    }

    static String normalize(KeyMaterial material) {
        Long keyId = material.getKey() == null ? null : material.getKey().getId();
        return String.join("|",
                keyId == null ? "" : String.valueOf(keyId),
                String.valueOf(material.getVersion()),
                material.getState() == null ? "" : material.getState().name(),
                nz(material.getWrappedKey()),
                nz(material.getIv()),
                nz(material.getWrapAlgo()),
                KstTime.format(material.getActivationDate()));
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static boolean matches(String stored, String computed) {
        if (stored == null) {
            return true;
        }
        return MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), computed.getBytes(StandardCharsets.UTF_8));
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
