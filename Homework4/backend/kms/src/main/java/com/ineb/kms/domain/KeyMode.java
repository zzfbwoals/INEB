package com.ineb.kms.domain;

/** 대칭키 운영 모드. 비대칭키·HMAC 은 모드가 없다(null). ECB 는 제공하되 UI 에서 비권장으로 표기한다. */
public enum KeyMode {
    CBC,
    GCM,
    CTR,
    ECB
}
