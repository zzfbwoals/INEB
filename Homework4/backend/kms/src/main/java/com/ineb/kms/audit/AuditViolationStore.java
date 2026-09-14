package com.ineb.kms.audit;

import com.ineb.kms.domain.AuditLog;
import com.ineb.kms.domain.AuditLogShadow;
import com.ineb.kms.domain.AuditViolation;
import com.ineb.kms.domain.ChainRow;
import com.ineb.kms.repository.AuditLogRepository;
import com.ineb.kms.repository.AuditViolationRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그 변조 증거 저장 + 위반 표시 판정 (2026-09-11). 섀도 비교 결과에서 아직 기록되지 않은 삭제·삽입·수정 행을 스냅샷으로
 * 남기고, 검사 실패인데 AUDIT_CHAIN_VIOLATION 이 없거나 새 증거가 있으면 그 자리에서 기록한다.
 * 검증(check)은 읽기 전용 REPEATABLE READ 트랜잭션이므로 별도 트랜잭션(REQUIRES_NEW)으로 커밋한다.
 * <p><b>동시 검증 중복 방지(2026-09-14)</b>: 트리거 알림 핸들러의 즉시 재검증과, 그 DB_DIRECT_CHANGE 의 SSE 를 받은 화면의
 * chain-status 재조회(및 대시보드 summary)가 같은 순간 check() 를 돌리면 둘 다 "기록 없음"을 보고 VIOLATION 을 두 번 남겼다.
 * 그래서 증거 저장·존재 확인·기록을 한 트랜잭션에서 체인 advisory lock(AuditChainService.CHAIN_LOCK_KEY)을 잡은 뒤 수행한다 —
 * 뒤에 온 쪽은 앞쪽 커밋 후에 잠금을 얻고 READ COMMITTED 로 다시 확인하므로 건너뛴다. 기록(append)은 같은 트랜잭션에 참여해
 * 같은 잠금을 재진입한다(REQUIRES_NEW 로 새 연결을 열면 자기 잠금에 막혀 교착).</p>
 */
@Service
public class AuditViolationStore {

    private static final Logger log = LoggerFactory.getLogger(AuditViolationStore.class);

    /** 판정 결과 — 새로 남긴 증거, 위반 표시(영구), 기록에 쓴 detail */
    public record Settled(List<AuditViolation> added, boolean flagged, String detail) { }

    private final AuditViolationRepository repository;
    private final AuditLogRepository auditLogRepository;
    private final AuditChainService chainService;
    private final EntityManager entityManager;

    public AuditViolationStore(AuditViolationRepository repository, AuditLogRepository auditLogRepository,
                               AuditChainService chainService, EntityManager entityManager) {
        this.repository = repository;
        this.auditLogRepository = auditLogRepository;
        this.chainService = chainService;
        this.entityManager = entityManager;
    }

    /**
     * 증거 저장 + 위반 표시 판정을 체인 잠금 아래 한 번에. 검사 실패인데 기록이 없거나(잠금 안에서 재확인) 새 증거가 있으면
     * AUDIT_CHAIN_VIOLATION 을 남긴다. 표시 여부(true=영구 위반)는 증거·기록 어느 쪽이 남아 있어도 true.
     * @param summary 검사 요약 detail(violations=, rows=, shadow…) — 새 증거 요약이 뒤에 붙는다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Settled settle(AuditShadowComparer.Result result, boolean checksOk, String summary) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(" + AuditChainService.CHAIN_LOCK_KEY + ")").getResultList();
        List<AuditViolation> added = recordNew(result);
        String detail = summary + AuditLogService.evidenceDetail(added);
        boolean flagged = auditLogRepository.existsByAction(AuditLogService.CHAIN_VIOLATION);
        if (!added.isEmpty() || (!checksOk && !flagged)) {
            log.warn("감사로그 위반 감지 — 영구 위반 표시 기록: {}", detail);
            chainService.append(null, AuditLogService.CHAIN_VIOLATION, "AUDIT", detail);
            flagged = true;
        }
        return new Settled(added, flagged, detail);
    }

    /** 새로 발견된 증거만 저장하고 돌려준다 (없으면 빈 목록) — 호출자 트랜잭션(settle)에 참여 */
    List<AuditViolation> recordNew(AuditShadowComparer.Result result) {
        Instant now = Instant.now();
        List<AuditViolation> added = new ArrayList<>();
        for (AuditLogShadow s : result.deleted()) {
            add(added, AuditViolation.DELETED, "", s, now);
        }
        for (AuditLog a : result.inserted()) {
            add(added, AuditViolation.INSERTED, "", a, now);
        }
        for (AuditShadowComparer.Modified m : result.modified()) {
            add(added, AuditViolation.MODIFIED, String.join(",", m.fields()), m.current(), now);
        }
        return added;
    }

    private void add(List<AuditViolation> added, String kind, String fields, ChainRow snapshot, Instant now) {
        if (repository.existsByAuditIdAndKindAndFields(snapshot.getId(), kind, fields)) {
            return;
        }
        added.add(repository.save(new AuditViolation(kind, fields, snapshot, now)));
    }

    @Transactional(readOnly = true)
    public List<AuditViolation> all() {
        return repository.findAllByOrderByIdAsc();
    }
}
