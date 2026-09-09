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
 * DB 직접 조작(행 변조·삭제·삽입)은 애플리케이션 이벤트가 없어 실시간 브로드캐스트로 잡히지 않는다.
 * 그래서 주기적으로 원본 체인 + 섀도 비교 + 섀도 체인 + 보호 트리거를 한 번에 검증(healthy)하고, 상태가 바뀌는 순간에만
 * 감사 기록을 남긴다 — 기록(AUDIT_CHAIN_VIOLATION / AUDIT_CHAIN_RESTORED)은 체인 append 를 거치므로
 * SSE 브로드캐스트까지 자동으로 이어져 열려 있는 화면의 배지가 즉시 갱신된다. 이중 기록이라 이 행 자체는 섀도 차이를 만들지 않는다.
 * 상태 전이 시에만 기록하므로 위반이 지속돼도 매 주기 스팸이 쌓이지 않는다. 꼬리 삭제(마지막 행 삭제)는 체인이 못 잡지만 섀도 비교가 잡는다.
 */
@Component
public class AuditChainScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditChainScheduler.class);

    private final AuditLogService auditLogService;
    private final AuditChainService chainService;
    private final boolean enabled;

    /** 직전 검증 결과(healthy) — null 은 아직 미검증(기동 직후) */
    private Boolean lastHealthy;

    public AuditChainScheduler(AuditLogService auditLogService, AuditChainService chainService,
                               @Value("${kms.scheduler.audit-chain-check:true}") boolean enabled) {
        this.auditLogService = auditLogService;
        this.chainService = chainService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${kms.scheduler.interval-ms:60000}", initialDelayString = "${kms.scheduler.initial-delay-ms:30000}")
    public void tick() {
        if (!enabled) {
            return;
        }
        try {
            AuditLogService.Check check = auditLogService.check();
            AuditVerifyResponse result = check.response();
            if (!result.healthy() && !Boolean.FALSE.equals(lastHealthy)) {
                log.warn("감사로그 위반 감지: {}", check.detail());
                chainService.append(null, "AUDIT_CHAIN_VIOLATION", "AUDIT", check.detail());
            } else if (result.healthy() && Boolean.FALSE.equals(lastHealthy)) {
                log.info("감사로그 정상 복구: 전체 {}행", result.totalRows());
                chainService.append(null, "AUDIT_CHAIN_RESTORED", "AUDIT", check.detail());
            }
            lastHealthy = result.healthy();
        } catch (RuntimeException e) {
            log.error("감사로그 배치 검증 실패", e);
        }
    }
}
