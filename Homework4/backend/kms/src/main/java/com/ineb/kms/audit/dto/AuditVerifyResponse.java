package com.ineb.kms.audit.dto;

import java.util.List;

/** 체인 검증 결과 — 위반은 연속 구간(fromId~toId)으로 묶어 반환한다 */
public record AuditVerifyResponse(
        boolean valid,
        long totalRows,
        String verifiedAt,
        List<ViolationRange> violations) {

    public record ViolationRange(long fromId, long toId, String type) {
    }
}
