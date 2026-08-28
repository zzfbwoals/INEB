package com.ineb.kms.key.dto;

import com.ineb.kms.common.PageResponse;

/** GET /api/keys/{keyUid}/usage — 최근 30일 통계 + 사용 이력 페이지 */
public record UsageResponse(UsageStats stats, PageResponse<UsageItem> logs) {
}
