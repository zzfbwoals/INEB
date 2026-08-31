package com.ineb.kms.key.dto;

/**
 * 키값 조회 응답. material 은 언래핑된 키 재료 Base64
 * (대칭·HMAC: 원시 키 바이트, 비대칭: 개인키 PKCS#8). 비대칭이면 publicKey(X.509 Base64)도 함께.
 */
public record MaterialRevealResponse(
        int version,
        String state,
        String algorithm,
        int keySize,
        String material,
        String publicKey,
        String wrapAlgo) {
}
