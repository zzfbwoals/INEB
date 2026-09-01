package com.ineb.kms.user.dto;

/** ADMIN 한정 원문 조회 응답 — 복호화된 연락처·이메일 */
public record UserPlainResponse(
        long id,
        String name,
        String phone,
        String email) {
}
