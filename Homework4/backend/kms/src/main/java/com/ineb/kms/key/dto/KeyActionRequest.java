package com.ineb.kms.key.dto;

import com.ineb.kms.domain.KeyAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 상태 변경 연산 요청 (PATCH /api/keys/{keyUid}/status). 목표 상태를 직접 지정할 수 없다.
 * - activationDate: ACTIVATE 무시, ROTATE 시 미래면 새 버전 PRE_ACTIVE 예약
 * - version: REACTIVATE 필수 / DEACTIVATE·DESTROY 생략 시 키 전체 / ACTIVATE 생략 시 PRE_ACTIVE 버전 자동
 */
public record KeyActionRequest(
        @NotNull(message = "연산(action)을 지정해주세요.") KeyAction action,
        @NotBlank(message = "사유를 입력해주세요.")
        @Size(max = 500, message = "사유는 500자 이하여야 합니다.") String reason,
        String activationDate,
        Integer version) {
}
