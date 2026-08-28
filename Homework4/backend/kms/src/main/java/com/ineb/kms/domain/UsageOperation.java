package com.ineb.kms.domain;

/** 암복호화·서명검증 테스트 연산 종류. key_usage_log.operation 에 기록된다. */
public enum UsageOperation {
    ENCRYPT,
    DECRYPT,
    SIGN,
    VERIFY
}
