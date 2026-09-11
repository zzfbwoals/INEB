package com.ineb.kms.audit.dto;

/** detail 은 복호화한 원문. detailDecrypted=false 면 복호화 실패(암호화 이전 평문 행이거나 변조된 암호문) */
public record AuditLogItem(
        long id,
        String actor,
        String action,
        String target,
        String detail,
        boolean detailDecrypted,
        String createdAt) {
}
