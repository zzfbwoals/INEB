package com.ineb.kms.key.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 키값 조회 요청 — 사유 필수, 감사로그에 기록된다. */
public record MaterialRevealRequest(
        @NotBlank(message = "사유를 입력해주세요.")
        @Size(max = 500, message = "사유는 500자 이하여야 합니다.") String reason) {
}
