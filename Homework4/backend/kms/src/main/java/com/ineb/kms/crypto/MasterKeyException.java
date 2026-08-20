package com.ineb.kms.crypto;

/**
 * 마스터키 유도·검증 실패 예외. 기동 시점에 발생하면 애플리케이션 기동이 중단된다(fail-fast).
 */
public class MasterKeyException extends RuntimeException {

    public MasterKeyException(String message) {
        super(message);
    }

    public MasterKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
