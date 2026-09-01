package com.ineb.kms.audit;

import com.ineb.kms.domain.AuditLog;
import com.ineb.kms.repository.AuditLogRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * audit_log 체인 기록의 유일한 통로.
 * <p>
 * 두 트랜잭션이 동시에 마지막 행을 읽으면 같은 prev_hash 로 두 행이 생겨 체인이 갈라지므로,
 * PostgreSQL advisory lock(pg_advisory_xact_lock — 트랜잭션 커밋 시 자동 해제)으로 기록을 직렬화한다.
 * 마지막 행 SELECT FOR UPDATE 는 테이블이 비어 있을 때 잠글 행이 없어 첫 행부터 안전하지 않다.
 * <p>
 * 트랜잭션 경계: {@link #append}는 호출자 트랜잭션에 참여한다 — 본 작업이 롤백되면 감사 기록도 함께
 * 사라져 "성공한 작업 = 감사 기록 존재"가 보장된다. 로그인 실패처럼 예외를 던지는 경로는
 * {@link #appendDetached}(REQUIRES_NEW)로 기록을 남긴다.
 */
@Service
public class AuditChainService {

    /** advisory lock 키 — audit_log 체인 전용 임의 상수 (다른 lock 과 겹치지 않게 고정) */
    private static final long CHAIN_LOCK_KEY = 0x4B4D_5341_5544_54L;

    private static final int DETAIL_MAX = 500;

    private final AuditLogRepository repository;
    private final AuditHasher hasher;
    private final EntityManager entityManager;

    public AuditChainService(AuditLogRepository repository, AuditHasher hasher, EntityManager entityManager) {
        this.repository = repository;
        this.hasher = hasher;
        this.entityManager = entityManager;
    }

    @Transactional
    public void append(String actor, String action, String target, String detail) {
        doAppend(actor, action, target, detail);
    }

    /** 본 작업 트랜잭션과 분리해 기록 — 예외로 롤백되는 경로(로그인 실패 등) 전용 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendDetached(String actor, String action, String target, String detail) {
        doAppend(actor, action, target, detail);
    }

    private void doAppend(String actor, String action, String target, String detail) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(" + CHAIN_LOCK_KEY + ")").getResultList();
        String prevHash = repository.findTopByOrderByIdDesc()
                .map(AuditLog::getRowHash)
                .orElse(AuditLog.CHAIN_ANCHOR);
        String safeActor = actor == null || actor.isBlank() ? "SYSTEM" : actor;
        String safeDetail = detail == null ? ""
                : detail.substring(0, Math.min(detail.length(), DETAIL_MAX));
        Instant now = Instant.now();
        String rowHash = hasher.rowHash(prevHash, safeActor, action, target, safeDetail, now);
        repository.save(new AuditLog(safeActor, action, target, safeDetail, prevHash, rowHash, now));
    }
}
