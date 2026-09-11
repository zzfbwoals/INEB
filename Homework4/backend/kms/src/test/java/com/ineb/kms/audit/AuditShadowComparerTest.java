package com.ineb.kms.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ineb.kms.domain.AuditLog;
import com.ineb.kms.domain.AuditLogShadow;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 원본·섀도 병합 비교 — 삭제(중간·꼬리)·삽입·수정을 행 단위로 뽑고 같은 순회에서 양쪽 체인을 검증한다 */
class AuditShadowComparerTest {

    private final AuditHasher hasher = new AuditHasher(new byte[32]);
    private final AuditShadowComparer comparer = new AuditShadowComparer(new AuditChainVerifier(hasher));
    private final Instant base = Instant.parse("2026-09-09T00:00:00Z");

    /** 정상 체인 n행 (id 1..n) */
    private List<AuditLog> chain(int n) {
        List<AuditLog> rows = new ArrayList<>();
        String prev = AuditLog.CHAIN_ANCHOR;
        for (int i = 1; i <= n; i++) {
            Instant at = base.plusSeconds(i);
            String detail = "enc-" + i;
            String hash = hasher.rowHash(prev, "admin", "KEY_CREATED", "KEY#u", detail, at);
            rows.add(withId(new AuditLog("admin", "KEY_CREATED", "KEY#u", detail, prev, hash, at), i));
            prev = hash;
        }
        return rows;
    }

    private static List<AuditLogShadow> shadowOf(List<AuditLog> rows) {
        return rows.stream().map(r -> new AuditLogShadow(r.getId(), r.getActor(), r.getAction(), r.getTarget(),
                r.getDetail(), r.getPrevHash(), r.getRowHash(), r.getCreatedAt())).toList();
    }

    private AuditShadowComparer.Result compare(List<AuditLog> current, List<AuditLogShadow> shadow) {
        return comparer.compare(current.iterator(), shadow.iterator());
    }

    @Test
    @DisplayName("원본과 섀도가 같으면 차이가 없고 양쪽 체인 모두 정상이다")
    void identical() {
        List<AuditLog> rows = chain(5);
        AuditShadowComparer.Result r = compare(rows, shadowOf(rows));
        assertTrue(r.shadowClean());
        assertTrue(r.chain().valid());
        assertTrue(r.shadowChain().valid());
        assertEquals(5, r.currentRows());
        assertEquals(5, r.shadowRows());
    }

    @Test
    @DisplayName("중간 행이 지워지면 DELETED 로 섀도 내용이 나오고 원본 체인은 CHAIN_BROKEN 이다")
    void middleDeleted() {
        List<AuditLog> rows = chain(5);
        List<AuditLogShadow> shadow = shadowOf(rows);
        List<AuditLog> current = new ArrayList<>(rows);
        current.remove(2); // id 3
        AuditShadowComparer.Result r = compare(current, shadow);
        assertEquals(1, r.deletedCount());
        assertEquals(3L, r.deleted().getFirst().getId());
        assertEquals("enc-3", r.deleted().getFirst().getDetail());
        assertFalse(r.chain().valid());
        assertEquals(AuditChainVerifier.ViolationType.CHAIN_BROKEN, r.chain().violations().getFirst().type());
        assertTrue(r.shadowChain().valid());
    }

    @Test
    @DisplayName("꼬리 행 삭제는 체인으로는 잡히지 않지만 섀도 비교로 DELETED 가 잡힌다")
    void tailDeleted() {
        List<AuditLog> rows = chain(5);
        List<AuditLogShadow> shadow = shadowOf(rows);
        List<AuditLog> current = rows.subList(0, 3); // id 4,5 삭제
        AuditShadowComparer.Result r = compare(current, shadow);
        assertTrue(r.chain().valid());
        assertEquals(2, r.deletedCount());
        assertEquals(List.of(4L, 5L), r.deleted().stream().map(AuditLogShadow::getId).toList());
        assertFalse(r.shadowClean());
    }

    @Test
    @DisplayName("앱을 거치지 않고 끼워 넣은 행은 INSERTED 로 잡힌다")
    void inserted() {
        List<AuditLog> rows = chain(3);
        List<AuditLogShadow> shadow = shadowOf(rows);
        List<AuditLog> current = new ArrayList<>(rows);
        current.add(withId(new AuditLog("hacker", "KEY_DESTROYED", "KEY#u", "x", "fake", "fake", base), 99));
        AuditShadowComparer.Result r = compare(current, shadow);
        assertEquals(1, r.insertedCount());
        assertEquals(99L, r.inserted().getFirst().getId());
        assertEquals("hacker", r.inserted().getFirst().getActor());
        assertFalse(r.chain().valid());
    }

    @Test
    @DisplayName("필드가 바뀐 행은 MODIFIED 로 원본·현재와 달라진 필드 목록이 나온다")
    void modified() {
        List<AuditLog> rows = chain(3);
        List<AuditLogShadow> shadow = shadowOf(rows);
        AuditLog r2 = rows.get(1);
        List<AuditLog> current = new ArrayList<>(rows);
        current.set(1, withId(new AuditLog("intruder", r2.getAction(), r2.getTarget(), "tampered",
                r2.getPrevHash(), r2.getRowHash(), r2.getCreatedAt()), 2));
        AuditShadowComparer.Result r = compare(current, shadow);
        assertEquals(1, r.modifiedCount());
        AuditShadowComparer.Modified m = r.modified().getFirst();
        assertEquals(2L, m.current().getId());
        assertEquals("admin", m.original().getActor());
        assertEquals("intruder", m.current().getActor());
        assertEquals(List.of("actor", "detail"), m.fields());
        assertFalse(r.chain().valid()); // row_hash 재계산 불일치 = TAMPERED
        assertEquals(AuditChainVerifier.ViolationType.TAMPERED, r.chain().violations().getFirst().type());
    }

    @Test
    @DisplayName("양쪽 다 없는 id 갭(롤백 소비)은 차이가 아니다")
    void gapIsNotDiff() {
        List<AuditLog> rows = chain(4);
        List<AuditLog> current = new ArrayList<>(rows);
        current.remove(1); // id 2 는 양쪽 다 없음 — 하지만 체인은 깨짐
        List<AuditLogShadow> shadow = shadowOf(current);
        AuditShadowComparer.Result r = compare(current, shadow);
        assertTrue(r.shadowClean());
        assertFalse(r.chain().valid());
        assertFalse(r.shadowChain().valid()); // 섀도도 같은 구멍 — "양쪽 동일 변조" 신호
    }

    @Test
    @DisplayName("섀도가 통째로 비면 전 행이 INSERTED, 원본이 비면 전 행이 DELETED 이며 총계로 구분된다")
    void wholeTableMissing() {
        List<AuditLog> rows = chain(3);
        AuditShadowComparer.Result noShadow = compare(rows, List.of());
        assertEquals(3, noShadow.insertedCount());
        assertEquals(0, noShadow.shadowRows());
        AuditShadowComparer.Result noCurrent = compare(List.of(), shadowOf(rows));
        assertEquals(3, noCurrent.deletedCount());
        assertEquals(0, noCurrent.currentRows());
        AuditShadowComparer.Result empty = compare(List.of(), List.of());
        assertTrue(empty.shadowClean());
        assertTrue(empty.chain().valid());
    }

    @Test
    @DisplayName("keyset 순회는 500건 배치 경계를 넘어도 빠짐없이 이어진다")
    void keysetCrossesBatchBoundary() {
        List<AuditLog> rows = chain(1203);
        var it = AuditShadowComparer.keyset(lastId -> rows.stream()
                .filter(r -> r.getId() > lastId).limit(500).toList());
        long count = 0;
        long prev = 0;
        while (it.hasNext()) {
            AuditLog r = it.next();
            assertEquals(prev + 1, r.getId());
            prev = r.getId();
            count++;
        }
        assertEquals(1203, count);
    }

    @Test
    @DisplayName("감사 detail 요약은 key=value 관례와 id 구간 표기를 따른다")
    void summaryDetailFormat() {
        List<AuditLog> rows = chain(6);
        List<AuditLogShadow> shadow = shadowOf(rows);
        List<AuditLog> current = new ArrayList<>(rows.subList(0, 3)); // 4,5,6 삭제
        AuditShadowComparer.Result r = compare(current, shadow);
        String detail = AuditLogService.summaryDetail(r, AuditShadowGuard.Status.ACTIVE);
        assertEquals("violations=0, rows=3, shadowDeleted=3, shadowInserted=0, shadowModified=0, "
                + "shadowChainValid=true, shadowGuard=ACTIVE, deletedIds=4-6", detail);
    }

    private static AuditLog withId(AuditLog row, long id) {
        try {
            Field f = AuditLog.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(row, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return row;
    }
}
