package com.ineb.kms.notice.dto;

import java.util.List;

public record NoticeDetail(
        long id,
        String title,
        String content,
        boolean pinned,
        String createdBy,
        String authorName,
        long viewCount,
        List<NoticeFileItem> files,
        String createdAt,
        String updatedAt) {
}
