package com.ineb.kms.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeyAlgorithmTest {

    @Test
    @DisplayName("대칭 알고리즘만 운영 모드를 요구하고 SEED 는 CBC·ECB 만 허용한다")
    void symmetricModes() {
        assertTrue(KeyAlgorithm.AES.requiresMode());
        assertTrue(KeyAlgorithm.AES.supportsMode(KeyMode.GCM));
        assertTrue(KeyAlgorithm.SEED.supportsMode(KeyMode.CBC));
        assertFalse(KeyAlgorithm.SEED.supportsMode(KeyMode.GCM));
        assertFalse(KeyAlgorithm.RSA.requiresMode());
        assertFalse(KeyAlgorithm.RSA.supportsMode(KeyMode.CBC));
        assertFalse(KeyAlgorithm.SHA256.supportsMode(null));
    }

    @Test
    @DisplayName("알고리즘별 허용 사이즈가 검증된다")
    void sizes() {
        assertTrue(KeyAlgorithm.AES.supportsSize(192));
        assertFalse(KeyAlgorithm.SEED.supportsSize(256));
        assertTrue(KeyAlgorithm.RSA.supportsSize(4096));
        assertFalse(KeyAlgorithm.RSA.supportsSize(1024));
        assertTrue(KeyAlgorithm.ECDSA.supportsSize(384));
        assertTrue(KeyAlgorithm.SHA512.supportsSize(512));
    }

    @Test
    @DisplayName("용도는 알고리즘이 결정한다 — 대칭은 암복호화, RSA 는 둘 다, ECDSA·HMAC 은 서명검증")
    void defaultPurpose() {
        assertEquals(KeyPurpose.ENC_DEC, KeyAlgorithm.ARIA.defaultPurpose());
        assertEquals(KeyPurpose.ENC_DEC_SIGN_VERIFY, KeyAlgorithm.RSA.defaultPurpose());
        assertEquals(KeyPurpose.SIGN_VERIFY, KeyAlgorithm.ECDSA.defaultPurpose());
        assertEquals(KeyPurpose.SIGN_VERIFY, KeyAlgorithm.SHA256.defaultPurpose());

        assertTrue(KeyPurpose.ENC_DEC.canEncrypt());
        assertFalse(KeyPurpose.ENC_DEC.canSign());
        assertTrue(KeyPurpose.ENC_DEC_SIGN_VERIFY.canSign());
        assertFalse(KeyPurpose.SIGN_VERIFY.canEncrypt());
    }
}
