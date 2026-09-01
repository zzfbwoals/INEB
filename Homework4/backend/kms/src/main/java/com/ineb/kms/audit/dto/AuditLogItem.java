package com.ineb.kms.audit.dto;

public record AuditLogItem(
        long id,
        String actor,
        String action,
        String target,
        String detail,
        String createdAt) {
}
