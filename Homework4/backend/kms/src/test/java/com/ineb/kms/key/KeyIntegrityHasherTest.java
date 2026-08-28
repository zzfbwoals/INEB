package com.ineb.kms.key;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.DeactivationTrigger;
import com.ineb.kms.domain.KeyAlgorithm;
import com.ineb.kms.domain.KeyMaterial;
import com.ineb.kms.domain.KeyMode;
import com.ineb.kms.domain.KeyPurpose;
import com.ineb.kms.domain.KeyState;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeyIntegrityHasherTest {

    private final KeyIntegrityHasher hasher = new KeyIntegrityHasher(new byte[32]);

    private CryptoKey key() {
        return new CryptoKey("PAY-GW-AES256", KeyAlgorithm.AES, 256, KeyMode.GCM, KeyPurpose.ENC_DEC,
                true, 90, "설명");
    }

    @Test
    @DisplayName("crypto_key 정규화 문자열은 설계서 10.3 순서를 따르고 null 은 빈 문자열이다")
    void normalizeKey() {
        CryptoKey key = new CryptoKey("RSA-KEY", KeyAlgorithm.RSA, 2048, null, KeyPurpose.ENC_DEC_SIGN_VERIFY,
                false, null, null);
        String n = KeyIntegrityHasher.normalize(key);
        assertEquals(key.getKeyUid() + "|RSA-KEY|RSA|2048||ENC_DEC_SIGN_VERIFY|PRE_ACTIVE|1|false|", n);
    }

    @Test
    @DisplayName("key_material 정규화 문자열은 key_id|version|state|wrapped_key|iv|wrap_algo|activation_date(KST) 다")
    void normalizeMaterial() {
        KeyMaterial m = new KeyMaterial(key(), 2, KeyState.ACTIVE, "WRAPPED", "IVIV", null,
                Instant.parse("2026-08-28T00:00:00Z"));
        assertEquals("|2|ACTIVE|WRAPPED|IVIV|AES-256-GCM|2026-08-28 09:00:00", KeyIntegrityHasher.normalize(m));
    }

    @Test
    @DisplayName("같은 키로 계산한 해시는 재검증을 통과하고 상태가 바뀌면 불일치한다")
    void verifyAndTamper() {
        KeyMaterial m = new KeyMaterial(key(), 1, KeyState.ACTIVE, "W", "IV", null, Instant.now());
        hasher.rehash(m);
        assertEquals(64, m.getIntegrityHash().length());
        assertTrue(hasher.verify(m));

        m.transition(KeyState.DEACTIVATED, DeactivationTrigger.OPERATION);   // 재계산 없이 상태만 변경 = 변조
        assertFalse(hasher.verify(m));

        hasher.rehash(m);
        assertTrue(hasher.verify(m));
    }

    @Test
    @DisplayName("해시가 아직 없는 행은 위반으로 판정하지 않는다")
    void noHashIsValid() {
        assertTrue(hasher.verify(key()));
    }

    @Test
    @DisplayName("무결성 키가 다르면 해시가 다르다")
    void differentKeyDifferentHash() {
        CryptoKey key = key();
        String a = hasher.hash(key);
        byte[] other = new byte[32];
        other[0] = 1;
        String b = new KeyIntegrityHasher(other).hash(key);
        assertFalse(a.equals(b));
    }
}
