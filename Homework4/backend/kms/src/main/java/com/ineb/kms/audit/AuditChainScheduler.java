package com.ineb.kms.audit;

import com.ineb.kms.audit.dto.AuditVerifyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 감사로그 배치 검증 (키 무결성 배치와 같은 패턴).
 * <p>
 * DB 직접 조작(행 변조·삭제·삽입)의 실시간 감지는 audit_log 트리거 알림이 맡고, 이 배치는 알림을 놓친 변경(앱 정지 중 등)의 안전망이다.
 * 검증과 위반 기록은 {@link AuditLogService#check()} 한 곳에서 한다 — 검사(체인 + 섀도 비교 + 보호 트리거)가 실패했는데 아직
 * AUDIT_CHAIN_VIOLATION 이 없으면 그 자리에서 1건 기록하고, 이후에는 값을 원복해 검사가 통과해도 영구 위반이다(재해시 없음, 2026-09-11).
 * 기록은 체인 append 를 거쳐 SSE 브로드캐스트로 이어져 열린 화면의 배지가 즉시 갱신된다. 위반 표시가 DB(감사 로그)에 있으므로
 * 재기동해도 중복 기록되지 않고, AUDIT_CHAIN_RESTORED 는 더 이상 남기지 않는다.
 */
@Component
public class AuditChainScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditChainScheduler.class);

    private final AuditLogService auditLogService;
    private final boolean enabled;

    public AuditChainScheduler(AuditLogService auditLogService,
                               @Value("${kms.scheduler.audit-chain-check:true}") boolean enabled) {
        this.auditLogService = auditLogService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${kms.scheduler.interval-ms:60000}", initialDelayString = "${kms.scheduler.initial-delay-ms:30000}")
    public void tick() {
        if (!enabled) {
            return;
        }
        try {
            checkNow();
        } catch (RuntimeException e) {
            log.error("감사로그 배치 검증 실패", e);
        }
    }

    /** 즉시 재검증 — 배치와 audit_log 변경 알림(트리거)이 공유한다. 위반 기록 여부는 서비스가 판단한다 */
    public synchronized AuditVerifyResponse checkNow() {
        AuditLogService.Check check = auditLogService.check();
        if (!check.response().healthy()) {
            log.warn("감사로그 위반 상태: {}", check.detail());
        }
        return check.response();
    }
}
