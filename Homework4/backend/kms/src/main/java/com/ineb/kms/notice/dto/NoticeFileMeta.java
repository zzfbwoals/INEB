package com.ineb.kms.notice.dto;

import java.time.Instant;

/** JPQL 생성자 프로젝션 — enc_data 를 읽지 않는 첨부 메타. 래퍼 타입은 Hibernate 생성자 매칭용 */
public record NoticeFileMeta(
        Long id,
        Long noticeId,
        String originalName,
        String contentType,
        Long fileSize,
        Integer encVer,
        Instant createdAt) {
}
