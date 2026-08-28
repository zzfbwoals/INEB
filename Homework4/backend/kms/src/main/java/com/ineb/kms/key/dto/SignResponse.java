package com.ineb.kms.key.dto;

/** 서명 결과. signature = "{version}:{base64 sig}" */
public record SignResponse(String signature, int version) {
}
