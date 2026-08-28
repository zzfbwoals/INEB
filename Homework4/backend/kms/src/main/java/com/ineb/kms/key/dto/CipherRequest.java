package com.ineb.kms.key.dto;

import jakarta.validation.constraints.NotBlank;

/** 복호화 테스트 입력 — "{version}:{iv}:{ciphertext}" */
public record CipherRequest(@NotBlank(message = "암호문을 입력해주세요.") String ciphertext) {
}
