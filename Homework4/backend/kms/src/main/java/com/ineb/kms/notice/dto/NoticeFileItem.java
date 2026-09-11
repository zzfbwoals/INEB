package com.ineb.kms.notice.dto;

/** 상세 응답의 첨부 항목 — 암호문·IV 는 나가지 않는다 */
public record NoticeFileItem(
        long id,
        String originalName,
        long fileSize,
        int encVer,
        String createdAt) {
}
