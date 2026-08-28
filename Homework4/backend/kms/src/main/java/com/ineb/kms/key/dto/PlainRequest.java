package com.ineb.kms.key.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 암호화·서명 테스트 입력 (평문/원문). RSA 는 추가로 키 길이별 바이트 상한을 서비스에서 검사한다. */
public record PlainRequest(
        @NotBlank(message = "평문을 입력해주세요.")
        @Size(max = 4096, message = "평문은 4096자 이하여야 합니다.") String plaintext) {
}
