package com.ineb.kms.audit;

import com.ineb.kms.audit.dto.AuditLogItem;
import com.ineb.kms.audit.dto.AuditVerifyResponse;
import com.ineb.kms.common.KstTime;
import com.ineb.kms.common.PageResponse;
import com.ineb.kms.domain.AuditLog;
import com.ineb.kms.repository.AuditLogRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    /** CSV 내려받기 상한 — 초과분은 최신순으로 잘린다 */
    private static final int EXPORT_MAX_ROWS = 100_000;

    private final AuditLogRepository repository;
    private final AuditChainService chainService;
    private final AuditChainVerifier verifier;

    public AuditLogService(AuditLogRepository repository, AuditChainService chainService,
                           AuditChainVerifier verifier) {
        this.repository = repository;
        this.chainService = chainService;
        this.verifier = verifier;
    }

    /**
     * @param from "yyyy-MM-dd"(그날 00:00:00) 또는 "yyyy-MM-dd HH:mm:ss"
     * @param to   "yyyy-MM-dd"(그날 23:59:59) 또는 "yyyy-MM-dd HH:mm:ss"
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogItem> list(String actor, String action, String target,
                                           String from, String to, int page, int size) {
        Page<AuditLog> result = repository.findAll(spec(actor, action, target, from, to),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.of(result, AuditLogService::toItem);
    }

    /** CSV 내려받기 — 목록과 같은 필터. 내려받기 자체도 관리자 행위이므로 AUDIT_EXPORTED 로 기록한다. */
    @Transactional
    public String exportCsv(String actor, String action, String target, String from, String to,
                            String requestedBy) {
        Page<AuditLog> rows = repository.findAll(spec(actor, action, target, from, to),
                PageRequest.of(0, EXPORT_MAX_ROWS, Sort.by(Sort.Direction.DESC, "id")));
        StringBuilder sb = new StringBuilder("id,created_at,actor,action,target,detail\n");
        for (AuditLog row : rows.getContent()) {
            sb.append(row.getId()).append(',')
                    .append(csv(KstTime.format(row.getCreatedAt()))).append(',')
                    .append(csv(row.getActor())).append(',')
                    .append(csv(row.getAction())).append(',')
                    .append(csv(row.getTarget())).append(',')
                    .append(csv(row.getDetail())).append('\n');
        }
        chainService.append(requestedBy, "AUDIT_EXPORTED", "AUDIT", "rows=" + rows.getNumberOfElements());
        return sb.toString();
    }

    /**
     * 전체 체인 순차 검증 후 검증 행위 자체를 AUDIT_CHAIN_VERIFIED 로 기록한다.
     * 검증은 실행 시점까지 존재하는 행을 대상으로 하며, 방금 추가되는 검증 기록 행은 다음 검증부터 포함된다.
     */
    @Transactional
    public AuditVerifyResponse verify(String actor) {
        AuditChainVerifier.Result result = verifier.verify(this::iterateAll);
        chainService.append(actor, "AUDIT_CHAIN_VERIFIED", "AUDIT",
                "rows=" + result.totalRows() + ", valid=" + result.valid()
                        + (result.valid() ? "" : ", violations=" + result.violations().size()));
        return toResponse(result);
    }

    /** 조회 전용 체인 상태 — 같은 검증을 수행하되 감사 기록은 남기지 않는다 (화면 진입 시 자동 표시용) */
    @Transactional(readOnly = true)
    public AuditVerifyResponse status() {
        return toResponse(verifier.verify(this::iterateAll));
    }

    private static AuditVerifyResponse toResponse(AuditChainVerifier.Result result) {
        return new AuditVerifyResponse(result.valid(), result.totalRows(), KstTime.format(Instant.now()),
                result.violations().stream()
                        .map(v -> new AuditVerifyResponse.ViolationRange(v.fromId(), v.toId(), v.type().name()))
                        .toList());
    }

    private Specification<AuditLog> spec(String actor, String action, String target, String from, String to) {
        Instant fromAt = parseBound(from, " 00:00:00");
        Instant toAt = parseBound(to, " 23:59:59");
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            if (actor != null && !actor.isBlank()) {
                ps.add(cb.equal(root.get("actor"), actor.trim()));
            }
            if (action != null && !action.isBlank()) {
                ps.add(cb.equal(root.get("action"), action.trim()));
            }
            if (target != null && !target.isBlank()) {
                ps.add(cb.equal(root.get("target"), target.trim()));
            }
            if (fromAt != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromAt));
            }
            if (toAt != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("createdAt"), toAt));
            }
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /** id 오름차순 keyset 순회 — 전체를 한 번에 메모리에 올리지 않는다 */
    private java.util.Iterator<AuditLog> iterateAll() {
        return new java.util.Iterator<>() {
            private List<AuditLog> batch = repository.findFirst500ByIdGreaterThanOrderByIdAsc(0);
            private int index = 0;

            @Override
            public boolean hasNext() {
                if (index < batch.size()) {
                    return true;
                }
                if (batch.isEmpty()) {
                    return false;
                }
                batch = repository.findFirst500ByIdGreaterThanOrderByIdAsc(batch.getLast().getId());
                index = 0;
                return !batch.isEmpty();
            }

            @Override
            public AuditLog next() {
                return batch.get(index++);
            }
        };
    }

    private static AuditLogItem toItem(AuditLog row) {
        return new AuditLogItem(row.getId(), row.getActor(), row.getAction(), row.getTarget(),
                row.getDetail(), KstTime.format(row.getCreatedAt()));
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static Instant parseBound(String value, String timeSuffixForDateOnly) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        if (v.length() == 10) {
            v = v + timeSuffixForDateOnly;
        }
        return KstTime.parse(v);
    }
}
