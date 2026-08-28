package com.ineb.kms.key.dto;

/** 복호화 결과. oldVersion 은 현행이 아닌 버전으로 복호화된 경우(재암호화 대상 데이터). */
public record PlainResponse(String plaintext, int version, boolean oldVersion) {
}
