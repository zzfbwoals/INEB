package com.ineb.kms.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 개인정보 원문 조회 — 사유 필수, 감사로그(USER_PLAIN_VIEWED) 기록 */
public record PlainViewRequest(
        @NotBlank(message = "조회 사유는 필수 입력입니다.")
        @Size(max = 200, message = "조회 사유는 200자 이하여야 합니다.")
        String reason) {
}
