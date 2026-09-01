package com.ineb.kms.key;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ineb.kms.audit.AuditHook;
import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.DeactivationTrigger;
import com.ineb.kms.domain.KeyAlgorithm;
import com.ineb.kms.domain.KeyMaterial;
import com.ineb.kms.domain.KeyMode;
import com.ineb.kms.domain.KeyPurpose;
import com.ineb.kms.domain.KeyState;
import com.ineb.kms.repository.KeyMaterialRepository;
import com.ineb.kms.repository.KeyStatusHistoryRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeyIntegrityGuardTest {

    private KeyMaterialRepository materialRepository;
    private KeyIntegrityHasher hasher;
    private KeyStateMachine machine;
    private KeyIntegrityGuard guard;
    private final List<String> audits = new ArrayList<>();

    private CryptoKey key;
    private final List<KeyMaterial> materials = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        materialRepository = mock(KeyMaterialRepository.class);
        hasher = new KeyIntegrityHasher(new byte[32]);
        machine = new KeyStateMachine(materialRepository, mock(KeyStatusHistoryRepository.class), hasher);
        guard = new KeyIntegrityGuard(hasher, machine, materialRepository,
                (actor, action, target, detail) -> audits.add(action + ":" + detail));

        key = new CryptoKey("K", KeyAlgorithm.AES, 256, KeyMode.GCM, KeyPurpose.ENC_DEC, true, 90, null);
        setId(key, 1L);
        when(materialRepository.findByKeyIdOrderByVersionDesc(anyLong())).thenReturn(materials);
        when(materialRepository.findByKeyIdAndState(anyLong(), org.mockito.ArgumentMatchers.eq(KeyState.ACTIVE)))
                .thenAnswer(inv -> materials.stream().filter(m -> m.getState() == KeyState.ACTIVE).toList());
        when(materialRepository.findByStateNot(KeyState.DESTROYED))
                .thenAnswer(inv -> materials.stream().filter(m -> m.getState() != KeyState.DESTROYED).toList());
    }

    private KeyMaterial material(int version, KeyState state) throws Exception {
        KeyMaterial m = new KeyMaterial(key, version, KeyState.PRE_ACTIVE, "W" + version, "IV", null, Instant.now());
        setId(m, 100L + version);
        if (state != KeyState.PRE_ACTIVE) {
            m.transition(state, null);
        }
        hasher.rehash(m);
        materials.add(m);
        return m;
    }

    private static void setId(Object entity, Long id) throws Exception {
        Field f = entity.getClass().getDeclaredField("id");
        f.setAccessible(true);
        f.set(entity, id);
    }

    @Test
    @DisplayName("해시가 일치하는 버전은 검증을 통과하고 상태가 바뀌지 않는다")
    void validPasses() throws Exception {
        KeyMaterial m = material(1, KeyState.ACTIVE);
        guard.verifyOrDeactivate(m);
        assertEquals(KeyState.ACTIVE, m.getState());
        assertTrue(guard.isValid(m));
        assertTrue(audits.isEmpty());
    }

    @Test
    @DisplayName("변조된 ACTIVE 버전은 자동 정지(trigger=INTEGRITY)되고 409 를 던진다")
    void tamperedActiveDeactivated() throws Exception {
        KeyMaterial m = material(1, KeyState.ACTIVE);
        key.pointCurrent(1);
        m.rescheduleActivation(Instant.now().plusSeconds(3600));   // 해시 재계산 없이 값 변경 = 변조
        assertFalse(guard.isValid(m));

        BusinessException e = assertThrows(BusinessException.class, () -> guard.verifyOrDeactivate(m));
        assertEquals(ErrorCode.KEY_INTEGRITY_VIOLATION, e.getErrorCode());
        assertEquals(KeyState.DEACTIVATED, m.getState());
        assertEquals(DeactivationTrigger.INTEGRITY, m.getDeactivationTrigger());
        assertTrue(m.isReactivatable());
        assertTrue(hasher.verify(m));          // 정지 후 해시는 다시 정합
        assertEquals(1, audits.size());
        assertTrue(audits.get(0).startsWith("KEY_INTEGRITY_VIOLATION:version=1"));
    }

    @Test
    @DisplayName("변조된 PRE_ACTIVE 버전은 정지 전이가 없으므로 삭제 처리된다")
    void tamperedPreActiveDestroyed() throws Exception {
        KeyMaterial m = material(1, KeyState.PRE_ACTIVE);
        m.rescheduleActivation(Instant.now().plusSeconds(9999));
        assertThrows(BusinessException.class, () -> guard.verifyOrDeactivate(m));
        assertEquals(KeyState.DESTROYED, m.getState());
        assertFalse(m.isMaterialAvailable());
    }

    @Test
    @DisplayName("crypto_key 메타가 변조되면 키의 모든 ACTIVE 버전이 정지된다")
    void keyTamperedDeactivatesAllActive() throws Exception {
        KeyMaterial v1 = material(1, KeyState.ACTIVE);
        KeyMaterial v2 = material(2, KeyState.ACTIVE);
        key.pointCurrent(2);
        hasher.rehash(key);
        assertTrue(guard.isValid(key));

        key.rename("TAMPERED", null);
        BusinessException e = assertThrows(BusinessException.class, () -> guard.verifyOrDeactivate(key));
        assertEquals(ErrorCode.KEY_INTEGRITY_VIOLATION, e.getErrorCode());
        assertEquals(KeyState.DEACTIVATED, v1.getState());
        assertEquals(KeyState.DEACTIVATED, v2.getState());
        assertEquals(KeyState.DEACTIVATED, key.getStatus());
        assertTrue(audits.stream().anyMatch(a -> a.contains("scope=KEY, deactivated=2")));
    }

    @Test
    @DisplayName("조회 시점 강제 — 위반 버전은 예외 없이 즉시 정지되고 정상 버전은 유지된다")
    void enforceOnReadDeactivatesTampered() throws Exception {
        KeyMaterial ok = material(1, KeyState.ACTIVE);
        KeyMaterial bad = material(2, KeyState.ACTIVE);
        key.pointCurrent(2);
        hasher.rehash(key);
        bad.rescheduleActivation(Instant.now().plusSeconds(1));   // 해시 재계산 없이 값 변경 = 변조

        guard.enforceOnRead(key);

        assertEquals(KeyState.ACTIVE, ok.getState());
        assertEquals(KeyState.DEACTIVATED, bad.getState());
        assertEquals(DeactivationTrigger.INTEGRITY, bad.getDeactivationTrigger());
        assertTrue(bad.isReactivatable());
        assertTrue(audits.stream().anyMatch(a -> a.startsWith("KEY_INTEGRITY_VIOLATION:version=2")));
        // 두 번째 조회는 이미 정지된 버전을 건너뛴다 (감사 중복 기록 없음)
        int before = audits.size();
        guard.enforceOnRead(key);
        assertEquals(before, audits.size());
    }

    @Test
    @DisplayName("배치 검증은 위반 버전만 정지하고 건수를 돌려준다")
    void sweep() throws Exception {
        KeyMaterial ok = material(1, KeyState.ACTIVE);
        KeyMaterial bad = material(2, KeyState.ACTIVE);
        key.pointCurrent(2);
        bad.rescheduleActivation(Instant.now().plusSeconds(1));

        assertEquals(1, guard.sweep());
        assertEquals(KeyState.ACTIVE, ok.getState());
        assertEquals(KeyState.DEACTIVATED, bad.getState());
        assertEquals(0, guard.sweep());
    }
}
