package com.ineb.kms.audit;

import com.ineb.kms.common.KstTime;
import com.ineb.kms.crypto.WrappedSecretStore;
import com.ineb.kms.domain.AuditLog;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * audit_log 행 해시 (HMAC-SHA256, 16진수 64자). 키는 crypto_key 와 같은 무결성 키를 쓴다.
 * 정규화(설계서 10.3 규칙 준용): prev_hash|actor|action|target|detail|created_at(KST) — null → "", 날짜 KST "yyyy-MM-dd HH:mm:ss".
 * prev_hash 가 입력에 포함되므로 행 하나만 바꿔도 체인 전체 검증에서 드러난다.
 */
@Component
public class AuditHasher {

    private static final String HMAC_ALGO = "HmacSHA256";

    private final WrappedSecretStore secretStore;
    private final byte[] fixedKey;

    @Autowired
    public AuditHasher(WrappedSecretStore secretStore) {
        this.secretStore = secretStore;
        this.fixedKey = null;
    }

    /** 테스트용 — 무결성 키를 직접 주입 */
    AuditHasher(byte[] integrityKey) {
        this.secretStore = null;
        this.fixedKey = integrityKey;
    }

    public String rowHash(String prevHash, String actor, String action, String target, String detail, Instant createdAt) {
        String normalized = String.join("|",
                nz(prevHash), nz(actor), nz(action), nz(target), nz(detail), KstTime.format(createdAt));
        return hmac(normalized);
    }

    /** 저장된 row_hash 와 재계산 값을 상수 시간 비교 */
    public boolean verifyRow(AuditLog row) {
        String computed = rowHash(row.getPrevHash(), row.getActor(), row.getAction(),
                row.getTarget(), row.getDetail(), row.getCreatedAt());
        return MessageDigest.isEqual(
                nz(row.getRowHash()).getBytes(StandardCharsets.UTF_8),
                computed.getBytes(StandardCharsets.UTF_8));
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
            throw new IllegalStateException("감사 로그 해시 계산에 실패했습니다", e);
        }
    }
}
