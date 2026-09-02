package com.ineb.kms.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ineb.kms.domain.AuditLog;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditHasherTest {

    private final byte[] key = new byte[32];
    private final AuditHasher hasher = new AuditHasher(key);
    private final Instant at = Instant.parse("2026-09-01T03:00:00Z");

    @Test
    @DisplayName("같은 입력은 같은 해시, prev_hash 가 다르면 다른 해시가 나온다 (체인 연결성)")
    void hashDependsOnPrev() {
        String a = hasher.rowHash("EMPTY", "admin", "LOGIN_SUCCESS", "AUTH#admin", "role=ADMIN", at);
        String b = hasher.rowHash("EMPTY", "admin", "LOGIN_SUCCESS", "AUTH#admin", "role=ADMIN", at);
        String c = hasher.rowHash("다른prev", "admin", "LOGIN_SUCCESS", "AUTH#admin", "role=ADMIN", at);
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(64, a.length());
    }

    @Test
    @DisplayName("행 내용 그대로면 verifyRow 통과, detail 이 바뀌면 실패한다")
    void verifyRowDetectsTamper() {
        String rowHash = hasher.rowHash("EMPTY", "admin", "KEY_CREATED", "KEY#uid-1", "algorithm=AES", at);
        AuditLog ok = new AuditLog("admin", "KEY_CREATED", "KEY#uid-1", "algorithm=AES", "EMPTY", rowHash, at);
        AuditLog tampered = new AuditLog("admin", "KEY_CREATED", "KEY#uid-1", "algorithm=RSA", "EMPTY", rowHash, at);
        assertTrue(hasher.verifyRow(ok));
        assertFalse(hasher.verifyRow(tampered));
    }

    @Test
    @DisplayName("created_at 은 KST yyyy-MM-dd HH:mm:ss 로 정규화된다 — 같은 초의 나노초 차이는 해시에 영향 없다")
    void createdAtNormalizedToSeconds() {
        String a = hasher.rowHash("EMPTY", "admin", "LOGOUT", "AUTH#admin", "", Instant.parse("2026-09-01T03:00:00.123Z"));
        String b = hasher.rowHash("EMPTY", "admin", "LOGOUT", "AUTH#admin", "", Instant.parse("2026-09-01T03:00:00.999Z"));
        assertEquals(a, b);
    }
}
