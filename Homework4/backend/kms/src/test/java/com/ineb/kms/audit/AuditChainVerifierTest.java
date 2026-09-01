package com.ineb.kms.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ineb.kms.domain.AuditLog;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditChainVerifierTest {

    private final byte[] key = new byte[32];
    private final AuditHasher hasher = new AuditHasher(key);
    private final AuditChainVerifier verifier = new AuditChainVerifier(hasher);
    private final Instant base = Instant.parse("2026-09-01T03:00:00Z");

    /** 정상 체인 rows 개 생성 (id 1..n) */
    private List<AuditLog> chain(int rows) {
        List<AuditLog> list = new ArrayList<>();
        String prev = AuditLog.CHAIN_ANCHOR;
        for (int i = 1; i <= rows; i++) {
            Instant at = base.plusSeconds(i);
            String detail = "detail-" + i;
            String rowHash = hasher.rowHash(prev, "admin", "KEY_CREATED", "KEY#uid", detail, at);
            list.add(withId(new AuditLog("admin", "KEY_CREATED", "KEY#uid", detail, prev, rowHash, at), i));
            prev = rowHash;
        }
        return list;
    }

    private static AuditLog withId(AuditLog row, long id) {
        try {
            Field f = AuditLog.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(row, id);
            return row;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("정상 체인은 통과한다 (빈 체인 포함)")
    void validChainPasses() {
        AuditChainVerifier.Result empty = verifier.verify(List.of());
        assertTrue(empty.valid());
        assertEquals(0, empty.totalRows());

        AuditChainVerifier.Result result = verifier.verify(chain(10));
        assertTrue(result.valid());
        assertEquals(10, result.totalRows());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    @DisplayName("행 내용을 바꾸면 그 행만 TAMPERED 로 잡히고 이후 행으로 번지지 않는다")
    void tamperedRowIsolated() {
        List<AuditLog> rows = chain(5);
        AuditLog r3 = rows.get(2);
        rows.set(2, withId(new AuditLog(r3.getActor(), r3.getAction(), r3.getTarget(),
                "변조된 내용", r3.getPrevHash(), r3.getRowHash(), r3.getCreatedAt()), 3));

        AuditChainVerifier.Result result = verifier.verify(rows);
        assertFalse(result.valid());
        assertEquals(1, result.violations().size());
        AuditChainVerifier.Violation v = result.violations().getFirst();
        assertEquals(AuditChainVerifier.ViolationType.TAMPERED, v.type());
        assertEquals(3, v.fromId());
        assertEquals(3, v.toId());
    }

    @Test
    @DisplayName("중간 행을 삭제하면 다음 행이 CHAIN_BROKEN 으로 잡힌다")
    void deletedRowBreaksChain() {
        List<AuditLog> rows = chain(5);
        rows.remove(2);   // id 3 삭제

        AuditChainVerifier.Result result = verifier.verify(rows);
        assertFalse(result.valid());
        assertEquals(1, result.violations().size());
        AuditChainVerifier.Violation v = result.violations().getFirst();
        assertEquals(AuditChainVerifier.ViolationType.CHAIN_BROKEN, v.type());
        assertEquals(4, v.fromId());
    }

    @Test
    @DisplayName("체인에 없는 행을 끼워 넣으면 삽입 행과 다음 행에서 단절이 드러난다")
    void insertedRowDetected() {
        List<AuditLog> rows = chain(4);
        String fakeHash = hasher.rowHash("가짜prev", "hacker", "KEY_DESTROYED", "KEY#uid", "몰래 삽입", base);
        rows.add(2, withId(new AuditLog("hacker", "KEY_DESTROYED", "KEY#uid", "몰래 삽입",
                "가짜prev", fakeHash, base), 99));

        AuditChainVerifier.Result result = verifier.verify(rows);
        assertFalse(result.valid());
        assertTrue(result.violations().stream()
                .anyMatch(v -> v.type() == AuditChainVerifier.ViolationType.CHAIN_BROKEN));
    }

    @Test
    @DisplayName("연속한 같은 유형의 위반은 하나의 구간(fromId~toId)으로 묶인다")
    void consecutiveViolationsMerged() {
        List<AuditLog> rows = chain(6);
        for (int i = 1; i <= 3; i++) {   // id 2~4 변조
            AuditLog r = rows.get(i);
            rows.set(i, withId(new AuditLog(r.getActor(), r.getAction(), r.getTarget(),
                    "변조-" + i, r.getPrevHash(), r.getRowHash(), r.getCreatedAt()), i + 1));
        }
        AuditChainVerifier.Result result = verifier.verify(rows);
        assertEquals(1, result.violations().size());
        AuditChainVerifier.Violation v = result.violations().getFirst();
        assertEquals(2, v.fromId());
        assertEquals(4, v.toId());
    }
}
