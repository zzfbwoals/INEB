package com.ineb.kms.crypto;

import jakarta.annotation.PreDestroy;
import java.util.Arrays;
import org.springframework.stereotype.Component;

/**
 * 기동 시 유도된 마스터키를 byte[]로만 보관한다.
 * String 변환 금지 (GC까지 힙에 남아 힙덤프로 노출됨).
 */
@Component
public class MasterKeyHolder {

    private byte[] masterKey;

    /** MasterKeyInitializer가 KCV 검증 통과 후 1회 호출한다. */
    void init(byte[] masterKey) {
        this.masterKey = masterKey;
    }

    public byte[] getKey() {
        if (masterKey == null) {
            throw new MasterKeyException("마스터키가 초기화되지 않았습니다");
        }
        return masterKey;
    }

    @PreDestroy
    public void destroy() {
        if (masterKey != null) {
            Arrays.fill(masterKey, (byte) 0);
            masterKey = null;
        }
    }
}
