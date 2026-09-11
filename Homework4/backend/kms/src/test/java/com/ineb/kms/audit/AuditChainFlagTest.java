package com.ineb.kms.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ineb.kms.crypto.PersonalDataCodec;
import com.ineb.kms.domain.AuditLog;
import com.ineb.kms.domain.AuditViolation;
import com.ineb.kms.repository.AuditLogRepository;
import com.ineb.kms.repository.AuditLogShadowRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 감사 체인 위반은 영구 — 검사 실패·새 증거를 보면 AUDIT_CHAIN_VIOLATION 을 남기고, 이후 검사가 통과해도 위반으로 남는다(재해시 없음) */
class AuditChainFlagTest {

    private AuditLogRepository repository;
    private AuditChainService chainService;
    private AuditLogService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        chainService = mock(AuditChainService.class);
        service = new AuditLogService(repository, mock(AuditLogShadowRepository.class), chainService,
                mock(AuditShadowComparer.class), mock(AuditShadowGuard.class), mock(PersonalDataCodec.class),
                mock(AuditViolationStore.class));
    }

    @Test
    @DisplayName("검사 통과 + 기록 없음 + 새 증거 없음 → 정상, 아무것도 기록하지 않는다")
    void cleanStaysClean() {
        when(repository.existsByAction(AuditLogService.CHAIN_VIOLATION)).thenReturn(false);
        assertFalse(service.settleViolation(true, false, "violations=0"));
        verify(chainService, never()).appendDetached(any(), any(), any(), any());
    }

    @Test
    @DisplayName("검사 실패를 처음 보면 별도 트랜잭션으로 VIOLATION 을 1건 남기고 위반이 된다")
    void firstFailureRecorded() {
        when(repository.existsByAction(AuditLogService.CHAIN_VIOLATION)).thenReturn(false);
        assertTrue(service.settleViolation(false, false, "violations=1"));
        verify(chainService, times(1)).appendDetached(isNull(), eq(AuditLogService.CHAIN_VIOLATION), eq("AUDIT"), eq("violations=1"));
    }

    @Test
    @DisplayName("이미 기록된 뒤에는 지속 위반을 재기록하지 않고, DB 를 원복해 검사가 통과해도 위반으로 남는다")
    void permanentOnceFlagged() {
        when(repository.existsByAction(AuditLogService.CHAIN_VIOLATION)).thenReturn(true);
        assertTrue(service.settleViolation(false, false, "violations=1"));
        assertTrue(service.settleViolation(true, false, "violations=0"));      // 원복 — 검사 통과해도 영구 위반
        verify(chainService, never()).appendDetached(any(), any(), any(), any());
    }

    @Test
    @DisplayName("이미 위반 중이라도 새 변조 증거(다른 행·다른 컬럼)가 발견되면 VIOLATION 을 다시 남긴다")
    void newEvidenceRecordedAgain() {
        when(repository.existsByAction(AuditLogService.CHAIN_VIOLATION)).thenReturn(true);
        assertTrue(service.settleViolation(false, true, "violations=2, newModified=78(target)"));
        verify(chainService, times(1)).appendDetached(isNull(), eq(AuditLogService.CHAIN_VIOLATION), eq("AUDIT"),
                eq("violations=2, newModified=78(target)"));
    }

    @Test
    @DisplayName("증거 요약 detail — 종류별 id 와 바뀐 컬럼을 붙인다")
    void evidenceDetail() {
        AuditLog row = new AuditLog("admin", "KEY_CREATED", "KEY#1", "d", "EMPTY", "h", Instant.now());
        java.lang.reflect.Field f;
        try {
            f = AuditLog.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(row, 77L);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        List<AuditViolation> added = List.of(
                new AuditViolation(AuditViolation.MODIFIED, "actor,detail", row, Instant.now()),
                new AuditViolation(AuditViolation.DELETED, "", row, Instant.now()));
        assertEquals(", newModified=77(actor,detail), newDeleted=77", AuditLogService.evidenceDetail(added));
        assertEquals("", AuditLogService.evidenceDetail(List.of()));
    }
}
