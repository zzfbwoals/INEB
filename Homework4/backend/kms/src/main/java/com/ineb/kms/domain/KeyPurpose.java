package com.ineb.kms.domain;

/**
 * 키 용도 3종 (네이버 클라우드 KMS 기준). 알고리즘이 용도를 결정하므로 등록 시 서버가 검증한다.
 */
public enum KeyPurpose {
    /** 암/복호화 (대칭키) */
    ENC_DEC,
    /** 암/복호화 및 서명/검증 (RSA) */
    ENC_DEC_SIGN_VERIFY,
    /** 서명/검증 (ECDSA, HMAC) */
    SIGN_VERIFY;

    public boolean canEncrypt() {
        return this != SIGN_VERIFY;
    }

    public boolean canSign() {
        return this != ENC_DEC;
    }
}
