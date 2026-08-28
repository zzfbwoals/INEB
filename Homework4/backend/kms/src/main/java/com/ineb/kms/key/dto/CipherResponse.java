package com.ineb.kms.key.dto;

/** 암호화 결과. ciphertext = "{version}:{iv}:{ct}" (버전·IV 내장) */
public record CipherResponse(String ciphertext, int version) {
}
