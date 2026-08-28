package com.ineb.kms.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CryptoKeyTest {

    private CryptoKey key() {
        return new CryptoKey("PAY-GW-AES256", KeyAlgorithm.AES, 256, KeyMode.GCM, KeyPurpose.ENC_DEC,
                true, 90, "테스트");
    }

    private KeyMaterial material(CryptoKey key, int version, KeyState state) {
        KeyMaterial m = new KeyMaterial(key, version, KeyState.PRE_ACTIVE, "wrapped", "iv", null, Instant.now());
        m.transition(state, state == KeyState.DEACTIVATED ? DeactivationTrigger.OPERATION : null);
        return m;
    }

    @Test
    @DisplayName("ACTIVE 버전이 하나라도 있으면 키 상태는 ACTIVE 다")
    void statusActiveWhenAnyActive() {
        CryptoKey key = key();
        key.recalcStatus(List.of(material(key, 1, KeyState.DEACTIVATED), material(key, 2, KeyState.ACTIVE),
                material(key, 3, KeyState.PRE_ACTIVE)));
        assertEquals(KeyState.ACTIVE, key.getStatus());
    }

    @Test
    @DisplayName("ACTIVE 가 없고 PRE_ACTIVE 가 있으면 키 상태는 PRE_ACTIVE 다")
    void statusPreActive() {
        CryptoKey key = key();
        key.recalcStatus(List.of(material(key, 1, KeyState.DEACTIVATED), material(key, 2, KeyState.PRE_ACTIVE)));
        assertEquals(KeyState.PRE_ACTIVE, key.getStatus());
    }

    @Test
    @DisplayName("모든 버전이 DEACTIVATED 또는 DESTROYED 혼재면 키 상태는 DEACTIVATED 다")
    void statusDeactivated() {
        CryptoKey key = key();
        key.recalcStatus(List.of(material(key, 1, KeyState.DESTROYED), material(key, 2, KeyState.DEACTIVATED)));
        assertEquals(KeyState.DEACTIVATED, key.getStatus());
    }

    @Test
    @DisplayName("모든 버전이 DESTROYED 면 키 상태는 DESTROYED 다")
    void statusDestroyed() {
        CryptoKey key = key();
        key.recalcStatus(List.of(material(key, 1, KeyState.DESTROYED), material(key, 2, KeyState.DESTROYED)));
        assertEquals(KeyState.DESTROYED, key.getStatus());
    }

    @Test
    @DisplayName("자동 갱신 키는 기준 시각 + 주기로 다음 갱신일이 계산된다")
    void rotationScheduled() {
        CryptoKey key = key();
        Instant base = Instant.parse("2026-08-28T00:00:00Z");
        key.changeRotation(true, 30, base);
        assertEquals(Instant.parse("2026-09-27T00:00:00Z"), key.getNextRotationAt());
        key.scheduleNextRotation(Instant.parse("2026-10-01T00:00:00Z"));
        assertEquals(Instant.parse("2026-10-31T00:00:00Z"), key.getNextRotationAt());
    }

    @Test
    @DisplayName("자동 갱신을 끄면 주기와 다음 갱신일이 비워지고, 키 정지 시에도 자동 갱신이 중단된다")
    void rotationDisabled() {
        CryptoKey key = key();
        key.changeRotation(false, 90, Instant.now());
        assertFalse(key.isAutoRotate());
        assertNull(key.getRotationPeriodDays());
        assertNull(key.getNextRotationAt());

        key.changeRotation(true, null, Instant.now());
        assertEquals(CryptoKey.ROTATION_DEFAULT_DAYS, key.getRotationPeriodDays());
        key.stopAutoRotation();
        assertFalse(key.isAutoRotate());
        assertNull(key.getNextRotationAt());
    }

    @Test
    @DisplayName("무결성 위반으로 정지된 버전만 재활성화 대상이다")
    void reactivatableOnlyIntegrity() {
        CryptoKey key = key();
        KeyMaterial byAdmin = material(key, 1, KeyState.DEACTIVATED);
        KeyMaterial byIntegrity = new KeyMaterial(key, 2, KeyState.ACTIVE, "w", "iv", null, Instant.now());
        byIntegrity.transition(KeyState.DEACTIVATED, DeactivationTrigger.INTEGRITY);

        assertFalse(byAdmin.isReactivatable());
        assertTrue(byIntegrity.isReactivatable());

        byIntegrity.transition(KeyState.ACTIVE, null);
        assertNull(byIntegrity.getDeactivationTrigger());
    }

    @Test
    @DisplayName("삭제된 버전은 재료가 비워지고 사용 불가로 판정된다")
    void destroyedMaterialUnavailable() {
        CryptoKey key = key();
        KeyMaterial m = material(key, 1, KeyState.DEACTIVATED);
        assertTrue(m.isMaterialAvailable());
        m.transition(KeyState.DESTROYED, null);
        m.destroyMaterial(Instant.now());
        assertFalse(m.isMaterialAvailable());
        assertNull(m.getWrappedKey());
    }
}
