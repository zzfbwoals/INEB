package com.ineb.kms.audit;

import com.ineb.kms.audit.dto.AuditForensicsResponse;
import com.ineb.kms.audit.dto.AuditLogItem;
import com.ineb.kms.audit.dto.AuditVerifyResponse;
import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.KstTime;
import com.ineb.kms.common.PageResponse;
import com.ineb.kms.crypto.PersonalDataCodec;
import com.ineb.kms.domain.AuditLog;
import com.ineb.kms.domain.ChainRow;
import com.ineb.kms.repository.AuditLogRepository;
import com.ineb.kms.repository.AuditLogShadowRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그 조회·검증. 검증은 {@link AuditShadowComparer} 로 원본 체인·섀도 비교·섀도 체인을 한 번에 계산하고,
 * 보호 트리거 상태({@link AuditShadowGuard})까지 합쳐 healthy 를 판정한다.
 * 검증 트랜잭션은 REPEATABLE READ — 배치 사이에 새 행이 커밋되면 원본·섀도 스냅샷이 어긋나 "삭제됨" 오탐이 나기 때문.
 */
@Service
public class AuditLogService {

    /** CSV 내려받기 상한 — 초과분은 최신순으로 잘린다 */
    private static final int EXPORT_MAX_ROWS = 100_000;

    /** 감사 detail 에 남기는 id 목록 상한(구간 표기) — detail 500자 안에 들어오도록 */
    private static final int ID_RANGES_MAX = 5;

    private static final java.util.Set<String> SORTABLE = java.util.Set.of("id", "createdAt", "actor", "action", "target");

    /** 검증 결과 + 감사 detail 문자열 (스케줄러·verify 공용) */
    public record Check(AuditVerifyResponse response, String detail) { }

    private final AuditLogRepository repository;
    private final AuditLogShadowRepository shadowRepository;
    private final AuditChainService chainService;
    private final AuditShadowComparer comparer;
    private final AuditShadowGuard guard;
    private final PersonalDataCodec codec;

    public AuditLogService(AuditLogRepository repository, AuditLogShadowRepository shadowRepository,
                           AuditChainService chainService, AuditShadowComparer comparer,
                           AuditShadowGuard guard, PersonalDataCodec codec) {
        this.repository = repository;
        this.shadowRepository = shadowRepository;
        this.chainService = chainService;
        this.comparer = comparer;
        this.guard = guard;
        this.codec = codec;
    }

    /**
     * @param from "yyyy-MM-dd"(그날 00:00:00) 또는 "yyyy-MM-dd HH:mm:ss"
     * @param to   "yyyy-MM-dd"(그날 23:59:59) 또는 "yyyy-MM-dd HH:mm:ss"
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogItem> list(String actor, String action, String target,
                                           String from, String to, int page, int size,
                                           String sort, String direction) {
        String field = sort != null && SORTABLE.contains(sort) ? sort : "id";
        Sort.Direction dir = sort == null ? Sort.Direction.DESC
                : "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Page<AuditLog> result = repository.findAll(spec(actor, action, target, from, to),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(dir, field).and(Sort.by(Sort.Direction.DESC, "id"))));
        return PageResponse.of(result, this::toItem);
    }

    /**
     * 통합 검색 — 최신 500건을 읽어 복호화한 뒤 action·actor·target·detail 부분일치(대소문자 무시). detail 은 마스터키 암호문이라
     * DB LIKE 가 불가하므로 상한을 두고 앱에서 비교한다. totalElements 는 그 500건 안의 일치 수.
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogItem> search(String q, int limit) {
        String needle = q.trim().toLowerCase();
        List<AuditLogItem> hits = new ArrayList<>();
        for (AuditLog row : repository.findFirst500ByOrderByIdDesc()) {
            AuditLogItem item = toItem(row);
            if (containsIgnoreCase(item.action(), needle) || containsIgnoreCase(item.actor(), needle)
                    || containsIgnoreCase(item.target(), needle) || containsIgnoreCase(item.detail(), needle)) {
                hits.add(item);
            }
        }
        int size = Math.min(Math.max(limit, 1), 100);
        List<AuditLogItem> page = hits.size() > size ? hits.subList(0, size) : hits;
        return new PageResponse<>(List.copyOf(page), 0, size, hits.size(),
                hits.isEmpty() ? 0 : (int) Math.ceil(hits.size() / (double) size));
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
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
                    .append(csv(plainDetail(row))).append('\n');
        }
        chainService.append(requestedBy, "AUDIT_EXPORTED", "AUDIT", "rows=" + rows.getNumberOfElements());
        return sb.toString();
    }

    /**
     * 전체 검증(원본 체인 + 섀도 비교 + 섀도 체인 + 보호 트리거) 후 검증 행위 자체를 AUDIT_CHAIN_VERIFIED 로 기록한다.
     * 검증은 실행 시점까지 존재하는 행을 대상으로 하며, 방금 추가되는 검증 기록 행은 다음 검증부터 포함된다.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public AuditVerifyResponse verify(String actor) {
        Check check = check();
        chainService.append(actor, "AUDIT_CHAIN_VERIFIED", "AUDIT", check.detail());
        return check.response();
    }

    /** 조회 전용 검증 — 감사 기록은 남기지 않는다 (감사 로그 화면 진입·SSE 갱신 시 자동 호출) */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AuditVerifyResponse status() {
        return check().response();
    }

    /** 스케줄러용 — 상태 전이 판정(healthy)과 기록할 detail 을 함께 돌려준다 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Check check() {
        AuditShadowComparer.Result result = compareAll();
        AuditShadowGuard.Status guardStatus = guard.status();
        return new Check(toResponse(result, guardStatus), summaryDetail(result, guardStatus));
    }

    /** 섀도 비교 상세 — 지워진·끼어든·바뀐 행의 내용(복호화). 화면의 "위반 상세" 모달용 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AuditForensicsResponse forensics() {
        AuditShadowComparer.Result r = compareAll();
        return new AuditForensicsResponse(KstTime.format(Instant.now()),
                r.chain().valid(), r.shadowChain().valid(), guard.status().name(),
                r.currentRows(), r.shadowRows(), r.deletedCount(), r.insertedCount(), r.modifiedCount(),
                r.deleted().stream().map(this::toItem).toList(),
                r.inserted().stream().map(this::toItem).toList(),
                r.modified().stream()
                        .map(m -> new AuditForensicsResponse.ModifiedItem(m.current().getId(),
                                toItem(m.current()), toItem(m.original()), m.fields()))
                        .toList());
    }

    private AuditShadowComparer.Result compareAll() {
        return comparer.compare(
                AuditShadowComparer.keyset(repository::findFirst500ByIdGreaterThanOrderByIdAsc),
                AuditShadowComparer.keyset(shadowRepository::findFirst500ByIdGreaterThanOrderByIdAsc));
    }

    private static AuditVerifyResponse toResponse(AuditShadowComparer.Result r, AuditShadowGuard.Status guardStatus) {
        // 섀도 체인은 판정에 넣지 않는다 — 원본이 변조된 뒤 정상 기록이 이어지면 그 행이 변조된 상태를 prev 로 물고 섀도에 복사되어
        // 섀도 체인도 필연적으로 끊기므로 독립 신호가 아니다. "원본·섀도 동일 변조"는 체인 위반 + 섀도 차이 0 으로 드러난다.
        boolean healthy = r.chain().valid() && r.shadowClean() && guardStatus == AuditShadowGuard.Status.ACTIVE;
        return new AuditVerifyResponse(r.chain().valid(), healthy, r.currentRows(), KstTime.format(Instant.now()),
                r.chain().violations().stream()
                        .map(v -> new AuditVerifyResponse.ViolationRange(v.fromId(), v.toId(), v.type().name()))
                        .toList(),
                new AuditVerifyResponse.ShadowSummary(r.deletedCount(), r.insertedCount(), r.modifiedCount(),
                        r.currentRows(), r.shadowRows(), r.shadowChain().valid(), guardStatus.name()));
    }

    /**
     * 감사 detail — 기존 key=value 관례. 예:
     * violations=2, rows=1234, shadowDeleted=3, shadowInserted=0, shadowModified=1, shadowChainValid=true, shadowGuard=ACTIVE, deletedIds=1201-1203, modifiedIds=77
     */
    static String summaryDetail(AuditShadowComparer.Result r, AuditShadowGuard.Status guardStatus) {
        StringBuilder sb = new StringBuilder()
                .append("violations=").append(r.chain().violations().size())
                .append(", rows=").append(r.currentRows())
                .append(", shadowDeleted=").append(r.deletedCount())
                .append(", shadowInserted=").append(r.insertedCount())
                .append(", shadowModified=").append(r.modifiedCount())
                .append(", shadowChainValid=").append(r.shadowChain().valid())
                .append(", shadowGuard=").append(guardStatus);
        appendIds(sb, "deletedIds", r.deleted().stream().map(ChainRow::getId).toList());
        appendIds(sb, "insertedIds", r.inserted().stream().map(ChainRow::getId).toList());
        appendIds(sb, "modifiedIds", r.modified().stream().map(m -> m.current().getId()).toList());
        return sb.toString();
    }

    /** 연속 id 는 구간(a-b)으로 묶고 최대 ID_RANGES_MAX 개까지만 붙인다 (초과는 "…") */
    private static void appendIds(StringBuilder sb, String key, List<Long> sortedIds) {
        if (sortedIds.isEmpty()) {
            return;
        }
        List<String> ranges = new ArrayList<>();
        long start = sortedIds.getFirst();
        long prev = start;
        for (int i = 1; i <= sortedIds.size(); i++) {
            long cur = i < sortedIds.size() ? sortedIds.get(i) : Long.MIN_VALUE;
            if (cur == prev + 1) {
                prev = cur;
                continue;
            }
            ranges.add(start == prev ? String.valueOf(start) : start + "-" + prev);
            start = cur;
            prev = cur;
        }
        String joined = ranges.stream().limit(ID_RANGES_MAX).collect(Collectors.joining(","));
        sb.append(", ").append(key).append('=').append(joined).append(ranges.size() > ID_RANGES_MAX ? ",…" : "");
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

    private AuditLogItem toItem(ChainRow row) {
        String plain;
        boolean decrypted;
        try {
            plain = codec.decrypt(row.getDetail());
            decrypted = true;
        } catch (BusinessException e) {
            plain = row.getDetail();
            decrypted = false;
        }
        return new AuditLogItem(row.getId(), row.getActor(), row.getAction(), row.getTarget(),
                plain, decrypted, KstTime.format(row.getCreatedAt()));
    }

    /** 화면·CSV 에는 복호화한 상세를 보여준다. 복호화가 안 되는 값(암호화 이전 평문 행)은 그대로 보여준다 */
    private String plainDetail(ChainRow row) {
        try {
            return codec.decrypt(row.getDetail());
        } catch (BusinessException e) {
            return row.getDetail();
        }
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
