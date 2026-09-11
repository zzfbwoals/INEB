package com.ineb.kms.audit.dto;

import java.util.List;

/**
 * 체인 검증 결과 — 위반은 연속 구간(fromId~toId)으로 묶어 반환한다.
 * valid 는 원본 체인만의 판정, healthy 는 체인 + 섀도 비교 + 보호 트리거를 합친 판정에 위반 표시(flagged)까지 더한 최종 판정
 * (shadowChainValid 는 참고용 — 판정 제외). flagged 는 AUDIT_CHAIN_VIOLATION 이 한 번이라도 기록됐는지 — 영구 위반(재해시 없음, 2026-09-11):
 * valid·섀도·트리거가 모두 정상인데 flagged 면 "값을 원복했지만 위반 이력이 남은" 상태다.
 */
public record AuditVerifyResponse(
        boolean valid,
        boolean healthy,
        boolean flagged,
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
