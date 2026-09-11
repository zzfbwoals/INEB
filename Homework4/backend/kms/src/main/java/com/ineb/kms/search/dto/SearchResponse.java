package com.ineb.kms.search.dto;

import com.ineb.kms.audit.dto.AuditLogItem;
import com.ineb.kms.key.dto.KeySummary;
import com.ineb.kms.notice.dto.NoticeSummary;
import com.ineb.kms.user.dto.UserSummary;
import java.util.List;

/**
 * 통합 검색 결과 — 항목별 목록은 기존 목록 DTO 를 그대로 쓴다.
 * type=ALL 이면 항목별 최대 5건, 단일 type 이면 그 항목만 최대 100건(나머지는 빈 배열).
 * counts 는 항목별 전체 일치 수(감사 로그는 최신 500건 안에서의 일치 수).
 */
public record SearchResponse(
        List<KeySummary> keys,
        List<UserSummary> users,
        List<NoticeSummary> notices,
        List<AuditLogItem> audits,
        Counts counts) {

    public record Counts(long keys, long users, long notices, long audits) {
    }
}
