package com.ineb.kms.config;

import com.ineb.kms.audit.AuditChainService;
import com.ineb.kms.audit.AuditLogService;
import com.ineb.kms.audit.AuditShadowGuard;
import com.ineb.kms.audit.dto.AuditVerifyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 감사 로그 스키마 보정 — `ddl-auto: update` 가 하지 못하는 것들을 기동 시 처리한다.
 * Hibernate DDL(EntityManagerFactory 초기화) 이후, 웹 서버가 요청을 받기 전에 실행되도록 SmartInitializingSingleton 을 쓴다.
 * <ol>
 *   <li>audit_log.detail varchar(500) → text (2026-09-09 detail 마스터키 암호화, 암호문은 base64 라 평문보다 길다)</li>
 *   <li>audit_log_shadow 보호 트리거 설치(멱등). 이미 설치돼 있는데 비활성·누락이면 조작 흔적이므로 재설치 전에
 *       AUDIT_SHADOW_GUARD_TAMPERED 를 기록해 체인에 고정한다</li>
 *   <li>섀도 백필 — 섀도가 비어 있고 원본에 행이 있을 때 **단 한 번만** 원본을 통째로 복사하고 AUDIT_SHADOW_BACKFILLED
 *       (경계 표식: 이 행 이전은 복사본, 이후는 동시 기록)를 남긴다. 매 기동 NOT EXISTS 복사는 금지 — DB 에 직접 끼워 넣은
 *       행이 재시작만으로 섀도에 세탁되어 "삽입" 증거가 사라진다. 백필 시점에 이미 변조·삭제된 내용은 섀도에도 같은 값으로
 *       들어가므로 섀도 비교로는 드러나지 않고 체인 검증으로만 탐지된다(백필 시점 체인 검증 결과를 detail 에 남긴다)</li>
 * </ol>
 * 같은 타입으로의 ALTER 는 PostgreSQL 에서 무해(no-op)라 매 기동마다 실행해도 된다.
 */
@Component
public class AuditLogSchemaMigration implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(AuditLogSchemaMigration.class);

    private final JdbcTemplate jdbc;
    private final AuditShadowGuard guard;
    private final AuditLogService auditLogService;
    private final AuditChainService chainService;

    public AuditLogSchemaMigration(JdbcTemplate jdbc, AuditShadowGuard guard,
                                   AuditLogService auditLogService, AuditChainService chainService) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.auditLogService = auditLogService;
        this.chainService = chainService;
    }

    @Override
    public void afterSingletonsInstantiated() {
        jdbc.execute("ALTER TABLE audit_log ALTER COLUMN detail TYPE text");
        log.info("audit_log.detail 컬럼 타입 text 확인 (마스터키 암호문 저장)");

        Long shadowRows = jdbc.queryForObject("SELECT count(*) FROM audit_log_shadow", Long.class);
        boolean firstInstall = shadowRows != null && shadowRows == 0;
        AuditShadowGuard.Status before = guard.status();
        if (!firstInstall && before != AuditShadowGuard.Status.ACTIVE) {
            log.warn("audit_log_shadow 보호 트리거가 {} 상태 — 조작 흔적을 기록하고 재설치", before);
            chainService.append(null, "AUDIT_SHADOW_GUARD_TAMPERED", "AUDIT", "guard=" + before);
        }
        guard.install();
        log.info("audit_log_shadow 보호 트리거 확인 (UPDATE/DELETE/TRUNCATE 차단)");

        if (firstInstall) {
            backfill();
        }
    }

    private void backfill() {
        Long originalRows = jdbc.queryForObject("SELECT count(*) FROM audit_log", Long.class);
        if (originalRows == null || originalRows == 0) {
            return;
        }
        AuditVerifyResponse chain = auditLogService.status();
        int copied = jdbc.update("INSERT INTO audit_log_shadow (id, actor, action, target, detail, prev_hash, row_hash, created_at) "
                + "SELECT id, actor, action, target, detail, prev_hash, row_hash, created_at FROM audit_log ORDER BY id");
        chainService.append(null, "AUDIT_SHADOW_BACKFILLED", "AUDIT",
                "rows=" + copied + ", chainValidAtBackfill=" + chain.valid() + ", violations=" + chain.violations().size());
        log.info("audit_log_shadow 최초 백필 {}행 (백필 시점 체인 valid={})", copied, chain.valid());
    }
}
