package com.ineb.kms.key.dto;

/** 서명 검증 결과. 서명 불일치는 예외가 아니라 valid=false 로 응답한다. */
public record VerifyResponse(boolean valid, int version, boolean oldVersion) {
}
