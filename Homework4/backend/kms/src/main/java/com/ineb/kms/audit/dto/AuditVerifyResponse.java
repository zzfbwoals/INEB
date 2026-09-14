package com.ineb.kms.audit.dto;

import java.util.List;

/**
 * 체인 검증 결과 — 위반은 연속 구간(fromId~toId)으로 묶어 반환한다.
 * valid 는 원본 체인만의 판정, checksOk 는 체인 + 섀도 비교(지금 검사), flagged 는 관리자가 아직 확인하지 않은 변조 증거가 있는지,
 * healthy 는 checksOk && !flagged 인 최종 판정 (shadowChainValid 는 참고용 — 판정 제외).
 * 색상점(2026-09-14): flagged → 빨강 / !flagged && !checksOk → 주황(확인 완료 · 원복 필요) / healthy → 초록.
 * 증거는 원복해도 남으므로(영구) 확인 전까지 flagged 가 유지되고, 확인은 AUDIT_VIOLATION_ACKNOWLEDGED 로 체인에 남는다.
 */
public record AuditVerifyResponse(
        boolean valid,
        boolean healthy,
        boolean flagged,
        /** 변조 증거(audit_violation)에 남은 행 수(중복 id 제외) — 원복해도 유지, 화면 배지 "체인 위반 N건" */
        long flaggedRows,
        long totalRows,
        String verifiedAt,
        List<ViolationRange> violations,
        ShadowSummary shadow,
        /** 지금 검사(체인 + 섀도 비교) 통과 여부 — 확인 완료 후 색을 정한다 */
        boolean checksOk,
        /** 미확인 증거 행 수 — 빨강 조건, 툴팁 "미확인 M건" */
        long unacknowledgedRows) {

    public record ViolationRange(long fromId, long toId, String type) {
    }

    /** 섀도 비교 요약 — 목록(원본 내용)은 GET /api/audit-logs/forensics 로 */
    public record ShadowSummary(
            long deleted,
            long inserted,
            long modified,
            long currentRows,
            long shadowRows,
            boolean shadowChainValid) {
    }
}
