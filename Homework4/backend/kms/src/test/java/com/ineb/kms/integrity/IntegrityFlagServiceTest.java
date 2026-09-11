package com.ineb.kms.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 위반 표시 = 감사 체인의 VIOLATION/RESEALED 마지막 기록 — 값 원복과 무관하게 재해시로만 해제된다 */
class IntegrityFlagServiceTest {

    private static final String USER = "USER#7";
    private static final String KEY = "KEY#abc";

    private final List<String> sink = new ArrayList<>();
    private final IntegrityFlagTestSupport.Fixture fx = IntegrityFlagTestSupport.create(
            (actor, action, target, detail) -> sink.add(actor + ":" + action + ":" + target));
    private final IntegrityFlagService flags = fx.flags();

    @Test
    @DisplayName("기록이 없으면 위반이 아니다")
    void emptyIsClean() {
        assertFalse(flags.isFlagged(USER));
        assertEquals(Set.of(), flags.flagged(List.of(USER, KEY)));
        assertEquals(Set.of(), flags.flaggedAll());
    }

    @Test
    @DisplayName("flag 는 처음 한 번만 SYSTEM 기록을 남기고, 대상 접두에 따라 USER_/KEY_ 행위명을 고른다")
    void flagOnce() {
        assertTrue(flags.flag(USER, "d"));
        assertFalse(flags.flag(USER, "d"));
        assertTrue(flags.flag(KEY, "d"));
        assertEquals(List.of("SYSTEM:USER_INTEGRITY_VIOLATION:USER#7", "SYSTEM:KEY_INTEGRITY_VIOLATION:KEY#abc"), sink);
        assertEquals(Set.of(USER, KEY), flags.flaggedAll());
    }

    @Test
    @DisplayName("재해시 기록 뒤에는 정상이고, 그 뒤 다시 위반이 기록되면 다시 위반이다(마지막 기록 기준)")
    void lastRecordWins() {
        flags.flag(USER, "d");
        flags.reseal(USER, "admin", "reason=x");
        assertFalse(flags.isFlagged(USER));
        assertEquals("admin:USER_INTEGRITY_RESEALED:USER#7", sink.getLast());

        assertTrue(flags.flag(USER, "again"));
        assertTrue(flags.isFlagged(USER));
    }

    @Test
    @DisplayName("조회 스냅샷: 해시 불일치를 처음 관찰하면 그 자리에서 기록하고, 원복돼 해시가 맞아도 표시 중이면 위반이다")
    void snapshotObservesAndRecords() {
        IntegrityFlagService.Snapshot first = flags.snapshot(List.of(USER));
        assertFalse(first.check(USER, false, "detected=LIST"));
        assertFalse(first.check(USER, false, "detected=LIST"));         // 같은 응답 안 중복 기록 없음
        assertEquals(1, sink.size());
        assertTrue(sink.getFirst().startsWith("SYSTEM:USER_INTEGRITY_VIOLATION"));

        IntegrityFlagService.Snapshot later = flags.snapshot(List.of(USER));
        assertFalse(later.check(USER, true, "restored"));               // 값은 맞지만 재해시 전
        assertTrue(later.isFlagged(USER));
        assertEquals(1, sink.size());

        flags.reseal(USER, "admin", "reason=y");
        assertTrue(flags.snapshotAll().check(USER, true, "ok"));
    }

    @Test
    @DisplayName("DB 에 직접 끼워 넣은 RESEALED 행(row_hash 불일치)은 해제로 인정하지 않는다")
    void forgedResealIgnored() {
        flags.flag(KEY, "d");
        IntegrityFlagTestSupport.insertForged(fx, IntegrityFlagService.KEY_RESEALED, KEY);
        assertTrue(flags.isFlagged(KEY));
    }
}
