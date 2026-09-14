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
import java.util.Map;
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
 * <p><b>확인(acknowledge, 2026-09-14)</b>: 위반 표시는 "미확인 증거가 있는가"다. 관리자가 사유와 함께 확인한 증거
 * ({@link AuditAcknowledgeService})는 빠지고, 체인만 깨진 구간(섀도 차이 없음)도 CHAIN 증거 1건으로 만들어 같은 규칙에 태운다 —
 * 그래야 확인할 대상 없이 영구 빨강으로 남는 경우가 없다.</p>
 */
@Service
public class AuditViolationStore {

    private static final Logger log = LoggerFactory.getLogger(AuditViolationStore.class);

    /**
     * 판정 결과 — 새로 남긴 증거, 위반 표시(flagged = 미확인 증거 있음), 기록에 쓴 detail, 미확인 증거 행 수, 전체 증거 행 수.
     * 색상점: flagged → 빨강 / !flagged && 검사 실패 → 주황(확인 완료·원복 필요) / !flagged && 검사 통과 → 초록
     */
    public record Settled(List<AuditViolation> added, boolean flagged, String detail, long unacknowledged, long evidenceRows) { }

    private final AuditViolationRepository repository;
    private final AuditLogRepository auditLogRepository;
    private final AuditChainService chainService;
    private final AuditAcknowledgeService acknowledgeService;
    private final EntityManager entityManager;

    public AuditViolationStore(AuditViolationRepository repository, AuditLogRepository auditLogRepository,
                               AuditChainService chainService, AuditAcknowledgeService acknowledgeService,
                               EntityManager entityManager) {
        this.repository = repository;
        this.auditLogRepository = auditLogRepository;
        this.chainService = chainService;
        this.acknowledgeService = acknowledgeService;
        this.entityManager = entityManager;
    }

    /**
     * 증거 저장 + 위반 표시 판정을 체인 잠금 아래 한 번에. 검사 실패인데 기록이 없거나(잠금 안에서 재확인) 새 증거가 있으면
     * AUDIT_CHAIN_VIOLATION 을 남긴다. 위반 표시(flagged)는 미확인 증거가 하나라도 있으면 true — 증거는 원복해도 남으므로
     * 관리자 확인 전까지 유지되고, 확인이 끝나면 검사 결과가 색을 정한다.
     * @param summary 검사 요약 detail(violations=, rows=, shadow…) — 새 증거 요약이 뒤에 붙는다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Settled settle(AuditShadowComparer.Result result, boolean checksOk, String summary) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(" + AuditChainService.CHAIN_LOCK_KEY + ")").getResultList();
        List<AuditViolation> added = recordNew(result);
        added.addAll(recordChainOnly(result, added));
        String detail = summary + AuditLogService.evidenceDetail(added);
        boolean recorded = auditLogRepository.existsByAction(AuditLogService.CHAIN_VIOLATION);
        if (!added.isEmpty() || (!checksOk && !recorded)) {
            log.warn("감사로그 위반 감지 — 위반 표시 기록: {}", detail);
            chainService.append(null, AuditLogService.CHAIN_VIOLATION, "AUDIT", detail);
        }
        List<AuditViolation> all = repository.findAllByOrderByIdAsc();   // 같은 트랜잭션 — 방금 저장한 증거 포함
        Map<Long, AuditAcknowledgeService.Ack> acks = acknowledgeService.acknowledged();
        long unacknowledged = all.stream().filter(v -> !acks.containsKey(v.getId())).count();
        return new Settled(added, unacknowledged > 0, detail, unacknowledged, all.size());
    }

    /**
     * 체인만 깨진 구간의 증거 — 위반 구간 [fromId, toId] 안에 삭제·삽입·수정 증거가 하나도 없으면(섀도 차이 없음) CHAIN 증거를 남긴다.
     * 스냅샷은 fromId 행의 현재 값, fields 는 "fromId-toId"(unique 로 구간당 1건). 구간의 시작 행이 없으면 건너뛴다.
     */
    List<AuditViolation> recordChainOnly(AuditShadowComparer.Result result, List<AuditViolation> justAdded) {
        List<AuditViolation> out = new ArrayList<>();
        if (result.chain().valid()) {
            return out;
        }
        List<AuditViolation> existing = new ArrayList<>(repository.findAllByOrderByIdAsc());
        existing.addAll(justAdded);
        Instant now = Instant.now();
        for (AuditChainVerifier.Violation v : result.chain().violations()) {
            boolean covered = existing.stream().anyMatch(e -> !AuditViolation.CHAIN.equals(e.getKind())
                    && e.getAuditId() >= v.fromId() && e.getAuditId() <= v.toId());
            if (covered) {
                continue;
            }
            auditLogRepository.findById(v.fromId())
                    .ifPresent(row -> add(out, AuditViolation.CHAIN, v.fromId() + "-" + v.toId(), row, now));
        }
        return out;
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
