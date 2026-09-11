package com.ineb.kms.audit.dto;

import java.util.List;

/**
 * 섀도 비교 상세 — 지워진 행(섀도 값), 끼어든 행, 바뀐 행(원본 vs 현재, 달라진 필드). 각 목록은 200건 상한.
 * chainValid 인데 목록이 비어 있으면 원본·섀도가 함께 변조된 것으로 봐야 한다(화면이 안내).
 */
public record AuditForensicsResponse(
        String checkedAt,
        boolean chainValid,
        boolean shadowChainValid,
        String guard,
        long currentRows,
        long shadowRows,
        long deletedCount,
        long insertedCount,
        long modifiedCount,
        List<AuditLogItem> deleted,
        List<AuditLogItem> inserted,
        List<ModifiedItem> modified) {

    public record ModifiedItem(long id, AuditLogItem current, AuditLogItem original, List<String> fields) {
    }
}
