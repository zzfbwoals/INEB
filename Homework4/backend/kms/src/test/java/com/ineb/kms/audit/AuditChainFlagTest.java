package com.ineb.kms.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ineb.kms.domain.AuditLog;
import com.ineb.kms.domain.AuditViolation;
import com.ineb.kms.repository.AuditLogRepository;
import com.ineb.kms.repository.AuditViolationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * 감사 체인 위반 판정(2026-09-14 확인 도입) — 증거는 영구히 남고, 위반 표시(빨강)는 "미확인 증거가 있는가"다.
 * 관리자가 확인(AUDIT_VIOLATION_ACKNOWLEDGED)하면 빠지고, 체인만 깨진 구간은 CHAIN 증거 1건으로 같은 규칙에 태운다.
 * 판정·기록은 체인 advisory lock 아래 한 트랜잭션(AuditViolationStore.settle)에서 — 동시 검증이 두 번 기록하지 않는다.
 */
class AuditChainFlagTest {

    private AuditLogRepository auditLogRepository;
    private AuditViolationRepository violationRepository;
    private AuditChainService chainService;
    private AuditAcknowledgeService acknowledgeService;
    private EntityManager entityManager;
    private Query lockQuery;
    private AuditViolationStore store;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        violationRepository = mock(AuditViolationRepository.class);
        chainService = mock(AuditChainService.class);
        acknowledgeService = mock(AuditAcknowledgeService.class);
        entityManager = mock(EntityManager.class);
        lockQuery = mock(Query.class);
        when(entityManager.createNativeQuery(contains("pg_advisory_xact_lock"))).thenReturn(lockQuery);
        when(acknowledgeService.acknowledged()).thenReturn(Map.of());
        when(violationRepository.findAllByOrderByIdAsc()).thenReturn(List.of());
        when(violationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        store = new AuditViolationStore(violationRepository, auditLogRepository, chainService, acknowledgeService, entityManager);
    }

    private static AuditLog row(long id) {
        AuditLog row = new AuditLog("admin", "KEY_CREATED", "KEY#1", "d", "EMPTY", "h", Instant.now());
        try {
            java.lang.reflect.Field f = AuditLog.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(row, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return row;
    }

    private static AuditViolation evidence(long evidenceId, AuditViolation v) {
        try {
            java.lang.reflect.Field f = AuditViolation.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(v, evidenceId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return v;
    }

    /** 섀도 비교 결과 — 체인 유효성·수정 행만 다르게. 체인 실패면 3~3 TAMPERED 구간 */
    private static AuditShadowComparer.Result result(boolean chainValid, List<AuditShadowComparer.Modified> modified) {
        AuditChainVerifier.Result chain = new AuditChainVerifier.Result(chainValid, 10, chainValid ? List.of()
                : List.of(new AuditChainVerifier.Violation(3, 3, AuditChainVerifier.ViolationType.TAMPERED)));
        return new AuditShadowComparer.Result(chain, chain, 10, 10, 0, 0, modified.size(), List.of(), List.of(), modified);
    }

    @Test
    @DisplayName("검사 통과 + 증거 없음 → 정상, 아무것도 기록하지 않는다")
    void cleanStaysClean() {
        AuditViolationStore.Settled s = store.settle(result(true, List.of()), true, "violations=0");
        assertFalse(s.flagged());
        assertEquals(0, s.unacknowledged());
        assertEquals("violations=0", s.detail());
        verify(chainService, never()).append(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("수정 증거를 처음 보면 체인 잠금 → 존재 확인 → VIOLATION 기록 순서로 남기고, 미확인 증거가 있어 위반(빨강)")
    void firstFailureRecordedUnderLock() {
        when(violationRepository.existsByAuditIdAndKindAndFields(3L, AuditViolation.MODIFIED, "actor")).thenReturn(false);
        AuditShadowComparer.Modified m = new AuditShadowComparer.Modified(row(3), null, List.of("actor"));
        AuditViolation saved = evidence(1, new AuditViolation(AuditViolation.MODIFIED, "actor", row(3), Instant.now()));
        when(violationRepository.save(any())).thenReturn(saved);
        when(violationRepository.findAllByOrderByIdAsc()).thenReturn(List.of(saved));

        AuditViolationStore.Settled s = store.settle(result(false, List.of(m)), false, "violations=1");

        assertTrue(s.flagged());
        assertEquals(1, s.unacknowledged());
        assertEquals("violations=1, newModified=3(actor)", s.detail());
        InOrder order = inOrder(lockQuery, auditLogRepository, chainService);
        order.verify(lockQuery).getResultList();
        order.verify(auditLogRepository).existsByAction(AuditLogService.CHAIN_VIOLATION);
        order.verify(chainService, times(1)).append(isNull(), eq(AuditLogService.CHAIN_VIOLATION), eq("AUDIT"), eq("violations=1, newModified=3(actor)"));
    }

    @Test
    @DisplayName("동시 검증: 뒤에 온 쪽은 잠금 안에서 증거·기록이 이미 있음을 보고 새로 기록하지 않는다")
    void concurrentSecondCallerSkips() {
        AuditViolation existing = evidence(1, new AuditViolation(AuditViolation.MODIFIED, "actor", row(3), Instant.now()));
        when(violationRepository.existsByAuditIdAndKindAndFields(3L, AuditViolation.MODIFIED, "actor")).thenReturn(true);
        when(violationRepository.findAllByOrderByIdAsc()).thenReturn(List.of(existing));
        when(auditLogRepository.existsByAction(AuditLogService.CHAIN_VIOLATION)).thenReturn(true);
        AuditShadowComparer.Modified m = new AuditShadowComparer.Modified(row(3), null, List.of("actor"));

        AuditViolationStore.Settled s = store.settle(result(false, List.of(m)), false, "violations=1");

        assertTrue(s.flagged());
        verify(violationRepository, never()).save(any());
        verify(chainService, never()).append(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("검토 없이 DB 를 원복해 검사가 통과해도 미확인 증거가 남아 있으면 여전히 위반(빨강)")
    void restoredWithoutAckStaysFlagged() {
        AuditViolation existing = evidence(1, new AuditViolation(AuditViolation.MODIFIED, "actor", row(3), Instant.now()));
        when(violationRepository.findAllByOrderByIdAsc()).thenReturn(List.of(existing));
        when(auditLogRepository.existsByAction(AuditLogService.CHAIN_VIOLATION)).thenReturn(true);

        AuditViolationStore.Settled s = store.settle(result(true, List.of()), true, "violations=0");

        assertTrue(s.flagged());
        assertEquals(1, s.unacknowledged());
        verify(chainService, never()).append(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("관리자가 증거를 전부 확인하면 위반 표시가 풀린다 — 검사 통과면 초록, 검사 실패 지속이면 주황(flagged=false)")
    void acknowledgedClearsFlag() {
        AuditViolation existing = evidence(1, new AuditViolation(AuditViolation.MODIFIED, "actor", row(3), Instant.now()));
        when(violationRepository.existsByAuditIdAndKindAndFields(3L, AuditViolation.MODIFIED, "actor")).thenReturn(true);
        when(violationRepository.findAllByOrderByIdAsc()).thenReturn(List.of(existing));
        when(auditLogRepository.existsByAction(AuditLogService.CHAIN_VIOLATION)).thenReturn(true);
        when(acknowledgeService.acknowledged()).thenReturn(Map.of(1L,
                new AuditAcknowledgeService.Ack(1, 3, "admin", Instant.now(), "검토 완료")));

        AuditShadowComparer.Modified m = new AuditShadowComparer.Modified(row(3), null, List.of("actor"));
        AuditViolationStore.Settled stillBroken = store.settle(result(false, List.of(m)), false, "violations=1");
        assertFalse(stillBroken.flagged());          // 주황 — 화면은 checksOk=false 로 구분
        assertEquals(0, stillBroken.unacknowledged());
        assertEquals(1, stillBroken.evidenceRows());

        AuditViolationStore.Settled restored = store.settle(result(true, List.of()), true, "violations=0");
        assertFalse(restored.flagged());             // 초록
        verify(chainService, never()).append(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("체인만 깨지고 섀도 차이가 없으면(동시 변조·백필 이전) 구간당 CHAIN 증거 1건을 만들어 확인 대상으로 삼는다")
    void chainOnlyViolationBecomesEvidence() {
        when(auditLogRepository.findById(3L)).thenReturn(Optional.of(row(3)));
        when(violationRepository.existsByAuditIdAndKindAndFields(3L, AuditViolation.CHAIN, "3-3")).thenReturn(false);
        AuditViolation saved = evidence(7, new AuditViolation(AuditViolation.CHAIN, "3-3", row(3), Instant.now()));
        when(violationRepository.save(any())).thenReturn(saved);
        when(violationRepository.findAllByOrderByIdAsc()).thenReturn(List.of(saved));

        AuditViolationStore.Settled s = store.settle(result(false, List.of()), false, "violations=1");

        assertEquals(1, s.added().size());
        assertEquals(AuditViolation.CHAIN, s.added().get(0).getKind());
        assertEquals("violations=1, newChain=3(3-3)", s.detail());
        assertTrue(s.flagged());
        verify(chainService, times(1)).append(isNull(), eq(AuditLogService.CHAIN_VIOLATION), eq("AUDIT"), eq("violations=1, newChain=3(3-3)"));
    }

    @Test
    @DisplayName("복사본 차이(수정 등)가 있으면 체인 끊김은 그 차이로 설명되므로 CHAIN 증거를 만들지 않는다 — 검증기는 끊김을 다음 행에 보고한다")
    void chainEvidenceSkippedWhenShadowDiffers() {
        AuditViolation existing = evidence(1, new AuditViolation(AuditViolation.MODIFIED, "actor", row(3), Instant.now()));
        when(violationRepository.findAllByOrderByIdAsc()).thenReturn(List.of(existing));
        when(violationRepository.existsByAuditIdAndKindAndFields(3L, AuditViolation.MODIFIED, "actor")).thenReturn(true);
        when(auditLogRepository.existsByAction(AuditLogService.CHAIN_VIOLATION)).thenReturn(true);
        AuditShadowComparer.Modified m = new AuditShadowComparer.Modified(row(3), null, List.of("actor"));

        store.settle(result(false, List.of(m)), false, "violations=1");

        verify(auditLogRepository, never()).findById(any());
        verify(violationRepository, never()).save(any());
    }

    @Test
    @DisplayName("증거 요약 detail — 종류별 id 와 바뀐 컬럼을 붙인다")
    void evidenceDetail() {
        AuditLog r = row(77);
        List<AuditViolation> added = List.of(
                new AuditViolation(AuditViolation.MODIFIED, "actor,detail", r, Instant.now()),
                new AuditViolation(AuditViolation.DELETED, "", r, Instant.now()));
        assertEquals(", newModified=77(actor,detail), newDeleted=77", AuditLogService.evidenceDetail(added));
        assertEquals("", AuditLogService.evidenceDetail(List.of()));
    }
}
