package com.ineb.kms.audit;

import com.ineb.kms.crypto.PersonalDataCodec;
import com.ineb.kms.domain.AuditLog;
import com.ineb.kms.repository.AuditLogRepository;
import com.ineb.kms.repository.AuditLogShadowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>
 * 상세(detail)는 개인정보와 같은 봉투(PersonalDataCodec, base64(iv|ct+tag))로 마스터키 암호화해 detail 컬럼에 저장한다.
 * <p>
 * 섀도 이중 기록: 같은 트랜잭션에서 audit_log_shadow 에도 같은 행을 넣는다. 트리거 복사가 아니라 앱이 두 번 쓰는 이유는
 * DB 에 직접 INSERT 된 행은 섀도에 없어 "삽입"으로 드러나기 때문이다. 섀도 INSERT 는 ON CONFLICT DO NOTHING 이라
 * 시퀀스 되감기 같은 비정상 상황에서도 관리자 작업이 실패하지 않고, 기존 섀도 행이 증거로 남는다.
 */
@Service
public class AuditChainService {

    /** advisory lock 키 — audit_log 체인 전용 임의 상수 (다른 lock 과 겹치지 않게 고정) */
    private static final long CHAIN_LOCK_KEY = 0x4B4D_5341_5544_54L;

    private static final int DETAIL_MAX = 500;

    private static final Logger log = LoggerFactory.getLogger(AuditChainService.class);

    private final AuditLogRepository repository;
    private final AuditLogShadowRepository shadowRepository;
    private final AuditHasher hasher;
    private final EntityManager entityManager;
    private final AuditEventStream eventStream;
    private final PersonalDataCodec codec;

    public AuditChainService(AuditLogRepository repository, AuditLogShadowRepository shadowRepository,
                             AuditHasher hasher, EntityManager entityManager, AuditEventStream eventStream,
                             PersonalDataCodec codec) {
        this.repository = repository;
        this.shadowRepository = shadowRepository;
        this.hasher = hasher;
        this.entityManager = entityManager;
        this.eventStream = eventStream;
        this.codec = codec;
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
        // 상세는 마스터키로 암호화한 값을 detail 에 저장하고, 체인 해시도 저장되는 암호문으로 계산한다 (검증에 마스터키 불필요)
        String encDetail = codec.encrypt(safeDetail);
        Instant now = Instant.now();
        String rowHash = hasher.rowHash(prevHash, safeActor, action, target, encDetail, now);
        AuditLog saved = repository.save(new AuditLog(safeActor, action, target, encDetail, prevHash, rowHash, now));
        // IDENTITY 는 persist 시 즉시 INSERT 되어 id 가 채워진다 — 같은 트랜잭션에서 섀도에 같은 id 로 복사
        int copied = shadowRepository.insertIgnore(saved.getId(), safeActor, action, target, encDetail, prevHash, rowHash, now);
        if (copied == 0) {
            log.warn("audit_log_shadow id={} 가 이미 존재해 복사를 건너뜀 — 시퀀스 되감기 등 비정상 상황, 기존 섀도 행을 증거로 유지", saved.getId());
        }
        eventStream.publish(action, target);   // 커밋 후 접속 중인 화면에 실시간 브로드캐스트
    }
}
