package com.ineb.kms.key.dto;

/** 사용 이력 행 (key_usage_log). oldVersion 은 현행이 아닌 버전으로 처리된 호출. */
public record UsageItem(
        int version,
        String operation,
        String result,
        String failReason,
        String usedAt,
        boolean oldVersion) {
}
