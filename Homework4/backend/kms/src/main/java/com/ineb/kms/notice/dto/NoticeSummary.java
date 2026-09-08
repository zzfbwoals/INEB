package com.ineb.kms.notice.dto;

/** 목록 행 — 본문은 제외, 첨부는 개수만 */
public record NoticeSummary(
        long id,
        String title,
        boolean pinned,
        String authorName,
        long viewCount,
        long fileCount,
        String createdAt) {
}
