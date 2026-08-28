package com.ineb.kms.key.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 서명 검증 입력 — 원문 + "{version}:{base64 sig}" */
public record VerifyRequest(
        @NotBlank(message = "원문을 입력해주세요.")
        @Size(max = 4096, message = "원문은 4096자 이하여야 합니다.") String message,
        @NotBlank(message = "서명값을 입력해주세요.") String signature) {
}
