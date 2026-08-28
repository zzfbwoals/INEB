package com.ineb.kms.key.dto;

/** 상태 전이 이력 행. fromState 가 null 이면 버전 생성 이력. */
public record HistoryItem(
        int version,
        String fromState,
        String toState,
        String reason,
        String trigger,
        String changedBy,
        String changedAt) {
}
