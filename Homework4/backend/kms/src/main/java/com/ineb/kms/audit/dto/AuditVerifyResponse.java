package com.ineb.kms.audit.dto;

import java.util.List;

/**
 * 체인 검증 결과 — 위반은 연속 구간(fromId~toId)으로 묶어 반환한다.
 * valid 는 원본 체인만의 판정, healthy 는 체인 + 섀도 비교 + 보호 트리거를 합친 판정 (shadowChainValid 는 참고용 — 판정 제외).
 */
public record AuditVerifyResponse(
        boolean valid,
        boolean healthy,
        long totalRows,
        String verifiedAt,
        List<ViolationRange> violations,
        ShadowSummary shadow) {

    public record ViolationRange(long fromId, long toId, String type) {
    }

    /** 섀도 비교 요약 — 목록(원본 내용)은 GET /api/audit-logs/forensics 로 */
    public record ShadowSummary(
            long deleted,
            long inserted,
            long modified,
            long currentRows,
            long shadowRows,
            boolean shadowChainValid,
            String guard) {
    }
}
