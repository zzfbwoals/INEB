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
        long currentRows,
        long shadowRows,
        long deletedCount,
        long insertedCount,
        long modifiedCount,
        /** 체인만 깨진 구간 수(섀도 차이 없음) */
        long chainCount,
        /** 미확인 증거 행 수 */
        long unacknowledgedRows,
        List<AuditLogItem> deleted,
        List<AuditLogItem> inserted,
        List<ModifiedItem> modified,
        List<ChainItem> chain,
        /** 증거 행마다의 확인 기록 — 화면이 감사 행 단위로 "전부 확인됐는가"를 판정하고 ID 에 체크를 덮는다 */
        List<EvidenceItem> evidence) {

    public record ModifiedItem(long id, AuditLogItem current, AuditLogItem original, List<String> fields) {
    }

    /** 체인만 깨진 구간 — id(fromId)~toId, current 는 fromId 행의 현재 값 */
    public record ChainItem(long id, long toId, AuditLogItem current) {
    }

    public record EvidenceItem(long id, long auditId, String kind, String fields, AckItem ack) {
    }

    public record AckItem(String by, String at, String reason) {
    }
}
