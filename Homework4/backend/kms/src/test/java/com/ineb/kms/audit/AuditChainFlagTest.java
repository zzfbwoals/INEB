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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * 감사 체인 위반은 영구 — 검사 실패·새 증거를 보면 AUDIT_CHAIN_VIOLATION 을 남기고, 이후 검사가 통과해도 위반으로 남는다(재해시 없음).
 * 판정·기록은 체인 advisory lock 아래 한 트랜잭션(AuditViolationStore.settle)에서 — 동시 검증이 두 번 기록하지 않는다(2026-09-14).
 */
class AuditChainFlagTest {

    private AuditLogRepository auditLogRepository;
    private AuditViolationRepository violationRepository;
    private AuditChainService chainService;
    private EntityManager entityManager;
    private Query lockQuery;
    private AuditViolationStore store;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        violationRepository = mock(AuditViolationRepository.class);
        chainService = mock(AuditChainService.class);
        entityManager = mock(EntityManager.class);
        lockQuery = mock(Query.class);
        when(entityManager.createNativeQuery(contains("pg_advisory_xact_lock"))).thenReturn(lockQuery);
        store = new AuditViolationStore(violationRepository, auditLogRepository, chainService, entityManager);
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

    /** 섀도 비교 결과 — 체인 유효성·수정 행만 다르게 */
    private static AuditShadowComparer.Result result(boolean chainValid, List<AuditShadowComparer.Modified> modified) {
        AuditChainVerifier.Result chain = new AuditChainVerifier.Result(chainValid, 10, chainValid ? List.of()
                : List.of(new AuditChainVerifier.Violation(1, 1, AuditChainVerifier.ViolationType.TAMPERED)));
        return new AuditShadowComparer.Result(chain, chain, 10, 10, 0, 0, modified.size(), List.of(), List.of(), modified);
    }

    @Test
    @DisplayName("검사 통과 + 기록 없음 + 새 증거 없음 → 정상, 아무것도 기록하지 않는다")
    void cleanStaysClean() {
        when(auditLogRepository.existsByAction(AuditLogService.CHAIN_VIOLATION)).thenReturn(false);
        AuditViolationStore.Settled s = store.settle(result(true, List.of()), true, "violations=0");
        assertFalse(s.flagged());
        assertEquals("violations=0", s.detail());
        verify(chainService, never()).append(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("검사 실패를 처음 보면 체인 잠금 → 존재 확인 → 기록 순서로 VIOLATION 을 1건 남기고 위반이 된다")
    void firstFailureRecordedUnderLock() {
        when(auditLogRepository.existsByAction(AuditLogService.CHAIN_VIOLATION)).thenReturn(false);
        AuditViolationStore.Settled s = store.settle(result(false, List.of()), false, "violations=1");
        assertTrue(s.flagged());
        InOrder order = inOrder(lockQuery, auditLogRepository, chainService);
        order.verify(lockQuery).getResultList();
        order.verify(auditLogRepository).existsByAction(AuditLogService.CHAIN_VIOLATION);
        order.verify(chainService, times(1)).append(isNull(), eq(AuditLogService.CHAIN_VIOLATION), eq("AUDIT"), eq("violations=1"));
    }

    @Test
    @DisplayName("동시 검증: 잠금 안에서 다시 확인했을 때 앞선 검증이 이미 기록했으면(exists=true) 건너뛴다 — 두 번 기록하지 않음")
    void concurrentSecondCallerSkips() {
        when(auditLogRepository.existsByAction(AuditLogService.CHAIN_VIOLATION)).thenReturn(true);
        AuditViolationStore.Settled s = store.settle(result(false, List.of()), false, "violations=1");
        assertTrue(s.flagged());
        verify(chainService, never()).append(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("이미 기록된 뒤 DB 를 원복해 검사가 통과해도 위반으로 남는다(영구)")
    void permanentOnceFlagged() {
        when(auditLogRepository.existsByAction(AuditLogService.CHAIN_VIOLATION)).thenReturn(true);
        assertTrue(store.settle(result(true, List.of()), true, "violations=0").flagged());
        verify(chainService, never()).append(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("이미 위반 중이라도 새 변조 증거(다른 행·다른 컬럼)가 발견되면 증거를 저장하고 VIOLATION 을 다시 남긴다")
    void newEvidenceRecordedAgain() {
        when(auditLogRepository.existsByAction(AuditLogService.CHAIN_VIOLATION)).thenReturn(true);
        when(violationRepository.existsByAuditIdAndKindAndFields(78L, AuditViolation.MODIFIED, "target")).thenReturn(false);
        when(violationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AuditShadowComparer.Modified m = new AuditShadowComparer.Modified(row(78), null, List.of("target"));
        AuditViolationStore.Settled s = store.settle(result(false, List.of(m)), false, "violations=2");
        assertTrue(s.flagged());
        assertEquals(1, s.added().size());
        assertEquals("violations=2, newModified=78(target)", s.detail());
        verify(chainService, times(1)).append(isNull(), eq(AuditLogService.CHAIN_VIOLATION), eq("AUDIT"), eq("violations=2, newModified=78(target)"));
    }

    @Test
    @DisplayName("같은 (행, 종류, 컬럼) 증거는 다시 저장하지 않는다 — 앞선 동시 검증이 저장한 증거를 잠금 뒤에 보면 새 증거 없음")
    void duplicateEvidenceSkipped() {
        when(auditLogRepository.existsByAction(AuditLogService.CHAIN_VIOLATION)).thenReturn(true);
        when(violationRepository.existsByAuditIdAndKindAndFields(78L, AuditViolation.MODIFIED, "target")).thenReturn(true);
        AuditShadowComparer.Modified m = new AuditShadowComparer.Modified(row(78), null, List.of("target"));
        AuditViolationStore.Settled s = store.settle(result(false, List.of(m)), false, "violations=2");
        assertTrue(s.added().isEmpty());
        verify(violationRepository, never()).save(any());
        verify(chainService, never()).append(any(), anyString(), anyString(), any());
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
