package com.ineb.kms.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 사유만 받는 요청 본문 — 무결성 재해시 등 감사 통제가 붙는 관리자 행위 공용 */
public record ReasonRequest(
        @NotBlank(message = "사유는 필수 입력입니다.")
        @Size(max = 200, message = "사유는 200자 이하여야 합니다.")
        String reason) {
}
