package com.ineb.kms.audit;

import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.crypto.PersonalDataCodec;
import com.ineb.kms.domain.AuditLog;
import com.ineb.kms.domain.AuditViolation;
import com.ineb.kms.repository.AuditLogRepository;
import com.ineb.kms.repository.AuditViolationRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그 위반 증거의 "확인(acknowledge)" (2026-09-14). 증거(audit_violation)는 영구히 남고 감사 로그는 append-only 라 앱이
 * 원복하지 않는다. 대신 관리자가 위반 상세(diff)를 검토했다는 사실을 사유와 함께 체인에 남기고, 검토가 끝난 증거는 "미확인"에서 빠진다.
 * <ul>
 *   <li>저장 = 체인 기록 {@code AUDIT_VIOLATION_ACKNOWLEDGED}(actor=관리자, target AUDIT, detail 에 증거 id·행·종류·컬럼·사유).
 *       증거 표에 확인 컬럼을 두면 DB 접근자가 자기 변조를 확인 처리해 초록으로 만들 수 있으므로 키·사용자 재해시와 같은 방식을 쓴다.</li>
 *   <li>판정은 {@code row_hash} 가 유효한 기록만 인정 — 무결성 HMAC 키 없이 끼워 넣은 확인 행은 무시된다({@link AuditHasher#verifyRow}).</li>
 *   <li>확인 단위는 증거 행(audit_violation.id). 화면은 감사 행(auditId) 단위로 확인하므로 그 행에 걸린 미확인 증거 전부를 한 번에 기록한다.</li>
 * </ul>
 * 제목 옆 색상점: 미확인 증거 있음 = 빨강 / 전부 확인했지만 검사 실패 지속(원복 안 됨) = 주황 / 전부 확인 + 검사 통과 = 초록.
 * 한계: 증거 행 자체를 DB 에서 지우면 미확인 수가 줄어든다 — audit_violation 도 DB 변경 알림 트리거 대상이라 DB_DIRECT_CHANGE 로 남는다.
 */
@Service
public class AuditAcknowledgeService {

    public static final String ACKNOWLEDGED = "AUDIT_VIOLATION_ACKNOWLEDGED";

    /** 확인 기록 — 누가·언제·왜 */
    public record Ack(long evidenceId, long auditId, String by, Instant at, String reason) { }

    private final AuditLogRepository auditLogRepository;
    private final AuditViolationRepository violationRepository;
    private final AuditChainService chainService;
    private final AuditHasher hasher;
    private final PersonalDataCodec codec;

    public AuditAcknowledgeService(AuditLogRepository auditLogRepository, AuditViolationRepository violationRepository,
                                   AuditChainService chainService, AuditHasher hasher, PersonalDataCodec codec) {
        this.auditLogRepository = auditLogRepository;
        this.violationRepository = violationRepository;
        this.chainService = chainService;
        this.hasher = hasher;
        this.codec = codec;
    }

    /** 유효한 확인 기록을 증거 id 별로 (같은 증거를 여러 번 확인했으면 마지막 기록) */
    @Transactional(readOnly = true)
    public Map<Long, Ack> acknowledged() {
        Map<Long, Ack> out = new HashMap<>();
        for (AuditLog row : auditLogRepository.findByActionInOrderByIdAsc(List.of(ACKNOWLEDGED))) {
            if (!hasher.verifyRow(row)) {
                continue;   // HMAC 키 없이 끼워 넣은 확인 — 무시
            }
            parse(row).ifPresent(a -> out.put(a.evidenceId(), a));
        }
        return out;
    }

    /**
     * 감사 행(auditId)에 걸린 미확인 증거를 전부 확인 처리 — 증거마다 1건씩 체인에 남긴다(호출자 트랜잭션, 체인 잠금은 append 가 잡는다).
     * 미확인 증거가 없으면 409.
     * @return 이번에 확인한 증거 수
     */
    @Transactional
    public int acknowledge(long auditId, String reason, String actor) {
        Map<Long, Ack> acks = acknowledged();
        List<AuditViolation> pending = violationRepository.findByAuditIdOrderByIdAsc(auditId).stream()
                .filter(v -> !acks.containsKey(v.getId())).toList();
        if (pending.isEmpty()) {
            throw new BusinessException(ErrorCode.AUDIT_VIOLATION_NOT_FLAGGED);
        }
        for (AuditViolation v : pending) {
            chainService.append(actor, ACKNOWLEDGED, "AUDIT", detailOf(v, reason));
        }
        return pending.size();
    }

    static String detailOf(AuditViolation v, String reason) {
        return "evidenceId=" + v.getId() + ", auditId=" + v.getAuditId() + ", kind=" + v.getKind()
                + ", fields=" + v.getFields() + ", reason=" + reason;
    }

    /** detail(마스터키 암호문)을 복호화해 evidenceId·auditId·사유를 뽑는다 — 형식이 다르면 무시 */
    java.util.Optional<Ack> parse(AuditLog row) {
        String plain;
        try {
            plain = codec.decrypt(row.getDetail());
        } catch (BusinessException e) {
            return java.util.Optional.empty();
        }
        Long evidenceId = field(plain, "evidenceId");
        Long auditId = field(plain, "auditId");
        if (evidenceId == null || auditId == null) {
            return java.util.Optional.empty();
        }
        int r = plain.indexOf("reason=");
        String reason = r >= 0 ? plain.substring(r + "reason=".length()) : "";
        return java.util.Optional.of(new Ack(evidenceId, auditId, row.getActor(), row.getCreatedAt(), reason));
    }

    private static Long field(String plain, String key) {
        int i = plain.indexOf(key + "=");
        if (i < 0) {
            return null;
        }
        int s = i + key.length() + 1;
        int e = s;
        while (e < plain.length() && Character.isDigit(plain.charAt(e))) {
            e++;
        }
        try {
            return e > s ? Long.parseLong(plain.substring(s, e)) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
