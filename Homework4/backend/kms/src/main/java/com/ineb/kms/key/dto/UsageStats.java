package com.ineb.kms.key.dto;

/** 최근 30일 테스트 호출 통계 (key_usage_log 집계). */
public record UsageStats(
        long total,
        long encrypt,
        long decrypt,
        long sign,
        long verify,
        long oldVersion,
        long failed) {
}
