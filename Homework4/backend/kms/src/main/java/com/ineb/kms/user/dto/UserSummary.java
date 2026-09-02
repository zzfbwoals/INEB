package com.ineb.kms.user.dto;

/** 목록·상세 공용 — 연락처·이메일은 마스킹된 값만 나간다 */
public record UserSummary(
        long id,
        String name,
        String phoneMasked,
        String emailMasked,
        String status,
        int encVer,
        boolean integrityValid,
        String createdAt,
        String updatedAt) {
}
