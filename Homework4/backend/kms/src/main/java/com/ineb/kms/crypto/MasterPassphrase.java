package com.ineb.kms.crypto;

import org.springframework.core.env.Environment;

/**
 * 유일한 보안 환경변수 KMS_MASTER_PASSPHRASE 로딩.
 * 설정 비밀(ENC) 복호화와 마스터키 유도 양쪽이 같은 규칙으로 읽도록 한 곳에 모은다.
 * 호출자가 반환된 char[]의 zeroize를 책임진다.
 */
public final class MasterPassphrase {

    private MasterPassphrase() {
    }

    public static char[] load(Environment environment) {
        String value = environment.getProperty(CryptoConstants.MASTER_PASSPHRASE_ENV);
        if (value == null || value.isBlank()) {
            throw new MasterKeyException(
                    "환경변수 " + CryptoConstants.MASTER_PASSPHRASE_ENV + "가 설정되지 않아 기동을 중단합니다");
        }
        return value.toCharArray();
    }
}
