package com.ineb.kms.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.crypto.PersonalDataCodec;
import com.ineb.kms.domain.AuditLog;
import com.ineb.kms.domain.AuditViolation;
import com.ineb.kms.repository.AuditLogRepository;
import com.ineb.kms.repository.AuditViolationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 위반 증거 확인(acknowledge) — 체인 기록에서 파생, row_hash 가 유효한 기록만 인정, 감사 행 단위로 미확인 증거 전부 기록 */
class AuditAcknowledgeServiceTest {

    private AuditLogRepository auditLogRepository;
    private AuditViolationRepository violationRepository;
    private AuditChainService chainService;
    private AuditHasher hasher;
    private PersonalDataCodec codec;
    private AuditAcknowledgeService service;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        violationRepository = mock(AuditViolationRepository.class);
        chainService = mock(AuditChainService.class);
        hasher = mock(AuditHasher.class);
        codec = mock(PersonalDataCodec.class);
        service = new AuditAcknowledgeService(auditLogRepository, violationRepository, chainService, hasher, codec);
    }

    private static AuditLog ackRow(String actor, String encDetail) {
        return new AuditLog(actor, AuditAcknowledgeService.ACKNOWLEDGED, "AUDIT", encDetail, "p", "h", Instant.now());
    }

    private static AuditViolation evidence(long id, long auditId, String kind, String fields) {
        AuditLog snapshot = new AuditLog("admin", "KEY_CREATED", "KEY#1", "d", "EMPTY", "h", Instant.now());
        try {
            java.lang.reflect.Field f = AuditLog.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(snapshot, auditId);
            AuditViolation v = new AuditViolation(kind, fields, snapshot, Instant.now());
            java.lang.reflect.Field g = AuditViolation.class.getDeclaredField("id");
            g.setAccessible(true);
            g.set(v, id);
            return v;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("확인 기록을 증거 id 별로 파싱한다 — row_hash 검증 실패(끼워 넣은 행)와 형식 불량은 무시")
    void acknowledgedDerivedFromValidRows() {
        AuditLog ok = ackRow("admin", "enc1");
        AuditLog forged = ackRow("intruder", "enc2");
        AuditLog garbage = ackRow("admin", "enc3");
        when(auditLogRepository.findByActionInOrderByIdAsc(List.of(AuditAcknowledgeService.ACKNOWLEDGED)))
                .thenReturn(List.of(ok, forged, garbage));
        when(hasher.verifyRow(ok)).thenReturn(true);
        when(hasher.verifyRow(forged)).thenReturn(false);
        when(hasher.verifyRow(garbage)).thenReturn(true);
        when(codec.decrypt("enc1")).thenReturn("evidenceId=5, auditId=21, kind=MODIFIED, fields=actor, reason=검토 완료, 원복 예정");
        when(codec.decrypt("enc2")).thenReturn("evidenceId=6, auditId=22, kind=DELETED, fields=, reason=x");
        when(codec.decrypt("enc3")).thenReturn("rows=3");

        Map<Long, AuditAcknowledgeService.Ack> acks = service.acknowledged();

        assertEquals(1, acks.size());
        AuditAcknowledgeService.Ack a = acks.get(5L);
        assertEquals(21, a.auditId());
        assertEquals("admin", a.by());
        assertEquals("검토 완료, 원복 예정", a.reason());   // 사유에 쉼표가 있어도 끝까지
        assertFalse(acks.containsKey(6L));
    }

    @Test
    @DisplayName("감사 행의 미확인 증거마다 AUDIT_VIOLATION_ACKNOWLEDGED 를 1건씩 남기고, 이미 확인된 증거는 건너뛴다")
    void acknowledgeRecordsPendingOnly() {
        when(auditLogRepository.findByActionInOrderByIdAsc(any())).thenReturn(List.of());
        AuditViolation done = evidence(1, 21, AuditViolation.MODIFIED, "actor");
        AuditViolation pending = evidence(2, 21, AuditViolation.MODIFIED, "detail");
        when(violationRepository.findByAuditIdOrderByIdAsc(21L)).thenReturn(List.of(done, pending));
        AuditLog ackDone = ackRow("admin", "enc");
        when(auditLogRepository.findByActionInOrderByIdAsc(List.of(AuditAcknowledgeService.ACKNOWLEDGED))).thenReturn(List.of(ackDone));
        when(hasher.verifyRow(ackDone)).thenReturn(true);
        when(codec.decrypt("enc")).thenReturn("evidenceId=1, auditId=21, kind=MODIFIED, fields=actor, reason=r");

        int n = service.acknowledge(21, "원인 확인", "admin");

        assertEquals(1, n);
        verify(chainService).append(eq("admin"), eq(AuditAcknowledgeService.ACKNOWLEDGED), eq("AUDIT"),
                eq("evidenceId=2, auditId=21, kind=MODIFIED, fields=detail, reason=원인 확인"));
        verify(chainService, never()).append(anyString(), anyString(), anyString(), eq("evidenceId=1, auditId=21, kind=MODIFIED, fields=actor, reason=원인 확인"));
    }

    @Test
    @DisplayName("미확인 증거가 없는 행을 확인하면 409 AUDIT_VIOLATION_NOT_FLAGGED")
    void nothingPendingIsConflict() {
        when(auditLogRepository.findByActionInOrderByIdAsc(any())).thenReturn(List.of());
        when(violationRepository.findByAuditIdOrderByIdAsc(99L)).thenReturn(List.of());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.acknowledge(99, "r", "admin"));
        assertTrue(ex.getMessage().contains(ErrorCode.AUDIT_VIOLATION_NOT_FLAGGED.name()) || ex.getErrorCode() == ErrorCode.AUDIT_VIOLATION_NOT_FLAGGED);
        verify(chainService, never()).append(any(), anyString(), anyString(), any());
    }
}
