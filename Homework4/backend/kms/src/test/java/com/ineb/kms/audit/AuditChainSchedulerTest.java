package com.ineb.kms.audit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ineb.kms.audit.dto.AuditVerifyResponse;
import com.ineb.kms.domain.AuditLog;
import com.ineb.kms.repository.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 체인 배치는 상태 전이 시에만 기록하며, 재기동 뒤에는 감사 로그의 마지막 VIOLATION/RESTORED 에서 직전 판정을 이어받는다 */
class AuditChainSchedulerTest {

    private AuditLogService auditLogService;
    private AuditChainService chainService;
    private AuditLogRepository repository;

    @BeforeEach
    void setUp() {
        auditLogService = mock(AuditLogService.class);
        chainService = mock(AuditChainService.class);
        repository = mock(AuditLogRepository.class);
    }

    private AuditChainScheduler scheduler() {
        return new AuditChainScheduler(auditLogService, chainService, repository, true);
    }

    private void chainIs(boolean healthy) {
        AuditVerifyResponse response = new AuditVerifyResponse(healthy, healthy, 10, "", List.of(), null);
        when(auditLogService.check()).thenReturn(new AuditLogService.Check(response, "violations=1"));
    }

    private void lastRecord(String action) {
        Optional<AuditLog> row = action == null ? Optional.empty()
                : Optional.of(new AuditLog("SYSTEM", action, "AUDIT", "", "EMPTY", "h", Instant.now()));
        when(repository.findTopByActionInOrderByIdDesc(anyCollection())).thenReturn(row);
    }

    @Test
    @DisplayName("재기동 후 첫 검사: 마지막 기록이 VIOLATION 이고 여전히 위반이면 다시 기록하지 않는다")
    void restartKeepsViolationWithoutDuplicate() {
        lastRecord("AUDIT_CHAIN_VIOLATION");
        chainIs(false);
        AuditChainScheduler s = scheduler();
        s.checkNow();
        s.checkNow();
        verify(chainService, never()).append(any(), any(), any(), any());
    }

    @Test
    @DisplayName("재기동 후 첫 검사: 마지막 기록이 VIOLATION 인데 지금은 정상이면 RESTORED 를 1건 남긴다")
    void restartRecordsRestoredWhenHealedMeanwhile() {
        lastRecord("AUDIT_CHAIN_VIOLATION");
        chainIs(true);
        scheduler().checkNow();
        verify(chainService, times(1)).append(isNull(), eq("AUDIT_CHAIN_RESTORED"), eq("AUDIT"), any());
    }

    @Test
    @DisplayName("기록이 없거나 마지막이 RESTORED 면 위반 발견 시 VIOLATION 을 1건 남기고, 지속 위반은 재기록하지 않는다")
    void freshViolationRecordedOnce() {
        lastRecord("AUDIT_CHAIN_RESTORED");
        chainIs(false);
        AuditChainScheduler s = scheduler();
        s.checkNow();
        s.checkNow();
        verify(chainService, times(1)).append(isNull(), eq("AUDIT_CHAIN_VIOLATION"), eq("AUDIT"), any());

        lastRecord(null);
        chainIs(false);
        scheduler().checkNow();
        verify(chainService, times(2)).append(isNull(), eq("AUDIT_CHAIN_VIOLATION"), eq("AUDIT"), any());
    }

    @Test
    @DisplayName("직전 판정 복원은 첫 검사 때 한 번만 읽는다")
    void restoreOnce() {
        lastRecord("AUDIT_CHAIN_RESTORED");
        chainIs(true);
        AuditChainScheduler s = scheduler();
        s.checkNow();
        s.checkNow();
        s.checkNow();
        verify(repository, times(1)).findTopByActionInOrderByIdDesc(anyCollection());
        verify(chainService, never()).append(any(), any(), any(), any());
    }
}
