package com.ineb.kms.key;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.DeactivationTrigger;
import com.ineb.kms.domain.HistoryTrigger;
import com.ineb.kms.domain.KeyAlgorithm;
import com.ineb.kms.domain.KeyMaterial;
import com.ineb.kms.domain.KeyMode;
import com.ineb.kms.domain.KeyPurpose;
import com.ineb.kms.domain.KeyState;
import com.ineb.kms.domain.KeyStatusHistory;
import com.ineb.kms.repository.KeyMaterialRepository;
import com.ineb.kms.repository.KeyStatusHistoryRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KeyStateMachineTest {

    private KeyMaterialRepository materialRepository;
    private KeyStatusHistoryRepository historyRepository;
    private KeyIntegrityHasher hasher;
    private KeyStateMachine machine;

    private CryptoKey key;
    private final List<KeyMaterial> materials = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        materialRepository = mock(KeyMaterialRepository.class);
        historyRepository = mock(KeyStatusHistoryRepository.class);
        hasher = new KeyIntegrityHasher(new byte[32]);
        machine = new KeyStateMachine(materialRepository, historyRepository, hasher);

        key = new CryptoKey("PAY-GW-AES256", KeyAlgorithm.AES, 256, KeyMode.GCM, KeyPurpose.ENC_DEC, true, 90, null);
        setId(key, 10L);
        when(materialRepository.findByKeyIdOrderByVersionDesc(anyLong())).thenReturn(materials);
    }

    private KeyMaterial material(int version, KeyState state) throws Exception {
        KeyMaterial m = new KeyMaterial(key, version, KeyState.PRE_ACTIVE, "W" + version, "IV", null, Instant.now());
        setId(m, 100L + version);
        if (state != KeyState.PRE_ACTIVE) {
            m.transition(state, state == KeyState.DEACTIVATED ? DeactivationTrigger.OPERATION : null);
        }
        materials.add(m);
        return m;
    }

    private static void setId(Object entity, Long id) throws Exception {
        Field f = entity.getClass().getDeclaredField("id");
        f.setAccessible(true);
        f.set(entity, id);
    }

    private BusinessException fails(Runnable r) {
        return assertThrows(BusinessException.class, r::run);
    }

    @Test
    @DisplayName("PRE_ACTIVE → ACTIVE 전이는 이력을 남기고 키 상태·해시를 재계산한다")
    void preActiveToActive() throws Exception {
        KeyMaterial m = material(1, KeyState.PRE_ACTIVE);
        machine.transition(m, KeyState.ACTIVE, HistoryTrigger.OPERATION, "즉시 활성", "admin");

        assertEquals(KeyState.ACTIVE, m.getState());
        assertEquals(KeyState.ACTIVE, key.getStatus());
        assertNotNull(m.getIntegrityHash());
        assertNotNull(key.getIntegrityHash());
        assertTrue(hasher.verify(m));
        assertTrue(hasher.verify(key));

        ArgumentCaptor<KeyStatusHistory> captor = ArgumentCaptor.forClass(KeyStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        KeyStatusHistory h = captor.getValue();
        assertEquals(KeyState.PRE_ACTIVE, h.getFromState());
        assertEquals(KeyState.ACTIVE, h.getToState());
        assertEquals("admin", h.getChangedBy());
        assertEquals(HistoryTrigger.OPERATION, h.getTrigger());
    }

    @Test
    @DisplayName("PRE_ACTIVE → DESTROYED 는 허용, PRE_ACTIVE → DEACTIVATED 는 금지된다")
    void preActiveRules() throws Exception {
        KeyMaterial ok = material(1, KeyState.PRE_ACTIVE);
        machine.transition(ok, KeyState.DESTROYED, HistoryTrigger.OPERATION, "잘못 등록", "admin");
        assertEquals(KeyState.DESTROYED, ok.getState());

        KeyMaterial bad = material(2, KeyState.PRE_ACTIVE);
        BusinessException e = fails(() -> machine.transition(bad, KeyState.DEACTIVATED, HistoryTrigger.OPERATION, "x", "admin"));
        assertEquals(ErrorCode.KEY_TRANSITION_NOT_ALLOWED, e.getErrorCode());
    }

    @Test
    @DisplayName("ACTIVE → DESTROYED 직행과 DESTROYED 에서 나가는 전이는 금지된다")
    void terminalRules() throws Exception {
        KeyMaterial active = material(1, KeyState.ACTIVE);
        assertEquals(ErrorCode.KEY_TRANSITION_NOT_ALLOWED,
                fails(() -> machine.transition(active, KeyState.DESTROYED, HistoryTrigger.OPERATION, "x", "admin")).getErrorCode());

        KeyMaterial destroyed = material(2, KeyState.DESTROYED);
        assertEquals(ErrorCode.KEY_TRANSITION_NOT_ALLOWED,
                fails(() -> machine.transition(destroyed, KeyState.ACTIVE, HistoryTrigger.REACTIVATE, "x", "admin")).getErrorCode());
    }

    @Test
    @DisplayName("이미 같은 상태면 409 충돌이다")
    void sameStateConflict() throws Exception {
        KeyMaterial active = material(1, KeyState.ACTIVE);
        assertEquals(ErrorCode.KEY_STATE_CONFLICT,
                fails(() -> machine.transition(active, KeyState.ACTIVE, HistoryTrigger.OPERATION, "x", "admin")).getErrorCode());
    }

    @Test
    @DisplayName("최신(current) 버전은 단독 정지할 수 없고 키 전체 정지로만 가능하다")
    void latestVersionDeactivate() throws Exception {
        KeyMaterial v1 = material(1, KeyState.ACTIVE);
        KeyMaterial v2 = material(2, KeyState.ACTIVE);
        key.pointCurrent(2);

        assertEquals(ErrorCode.KEY_LATEST_VERSION_DEACTIVATE,
                fails(() -> machine.transition(v2, KeyState.DEACTIVATED, HistoryTrigger.OPERATION, "x", "admin")).getErrorCode());

        machine.transition(v1, KeyState.DEACTIVATED, HistoryTrigger.OPERATION, "구 버전 정지", "admin");
        assertEquals(KeyState.DEACTIVATED, v1.getState());
        assertEquals(DeactivationTrigger.OPERATION, v1.getDeactivationTrigger());
        assertEquals(KeyState.ACTIVE, key.getStatus());

        machine.transitionKeyWide(v2, KeyState.DEACTIVATED, HistoryTrigger.OPERATION, "키 정지", "admin");
        assertEquals(KeyState.DEACTIVATED, key.getStatus());
    }

    @Test
    @DisplayName("무결성 위반 자동 정지는 최신 버전도 정지시키고 trigger 를 INTEGRITY 로 남긴다")
    void integrityDeactivateLatest() throws Exception {
        KeyMaterial v1 = material(1, KeyState.ACTIVE);
        key.pointCurrent(1);
        machine.transitionKeyWide(v1, KeyState.DEACTIVATED, HistoryTrigger.INTEGRITY, "해시 불일치", "SYSTEM");
        assertEquals(DeactivationTrigger.INTEGRITY, v1.getDeactivationTrigger());
        assertTrue(v1.isReactivatable());
    }

    @Test
    @DisplayName("관리자가 정지한 버전은 재활성화할 수 없고, 무결성 정지 버전은 REACTIVATE 로만 복구된다")
    void reactivateRules() throws Exception {
        KeyMaterial byAdmin = material(1, KeyState.DEACTIVATED);
        assertEquals(ErrorCode.KEY_REACTIVATE_NOT_ALLOWED,
                fails(() -> machine.transition(byAdmin, KeyState.ACTIVE, HistoryTrigger.REACTIVATE, "x", "admin")).getErrorCode());

        KeyMaterial byIntegrity = material(2, KeyState.ACTIVE);
        byIntegrity.transition(KeyState.DEACTIVATED, DeactivationTrigger.INTEGRITY);
        // 잘못된 트리거로는 불가
        assertEquals(ErrorCode.KEY_REACTIVATE_NOT_ALLOWED,
                fails(() -> machine.transition(byIntegrity, KeyState.ACTIVE, HistoryTrigger.OPERATION, "x", "admin")).getErrorCode());

        machine.transition(byIntegrity, KeyState.ACTIVE, HistoryTrigger.REACTIVATE, "오탐 확인", "admin");
        assertEquals(KeyState.ACTIVE, byIntegrity.getState());
        assertNull(byIntegrity.getDeactivationTrigger());
        assertEquals(KeyState.ACTIVE, key.getStatus());
    }

    @Test
    @DisplayName("DEACTIVATED → DESTROYED 후 모든 버전이 삭제되면 키 상태가 DESTROYED 가 된다")
    void destroyAll() throws Exception {
        KeyMaterial v1 = material(1, KeyState.DEACTIVATED);
        machine.transition(v1, KeyState.DESTROYED, HistoryTrigger.OPERATION, "파기", "admin");
        assertEquals(KeyState.DESTROYED, key.getStatus());
    }

    @Test
    @DisplayName("버전 생성 이력은 from=null 로 기록되고 키 상태가 재계산된다")
    void recordCreated() throws Exception {
        KeyMaterial v1 = material(1, KeyState.ACTIVE);
        machine.recordCreated(v1, HistoryTrigger.ROTATE, "갱신", "admin");

        ArgumentCaptor<KeyStatusHistory> captor = ArgumentCaptor.forClass(KeyStatusHistory.class);
        verify(historyRepository, times(1)).save(captor.capture());
        assertNull(captor.getValue().getFromState());
        assertEquals(KeyState.ACTIVE, captor.getValue().getToState());
        assertEquals(KeyState.ACTIVE, key.getStatus());
        assertNotNull(v1.getIntegrityHash());
    }

    @Test
    @DisplayName("리포지토리 목록에 없는 새 버전도 키 상태 계산에 포함된다")
    void newMaterialNotInRepositoryList() throws Exception {
        when(materialRepository.findByKeyIdOrderByVersionDesc(anyLong())).thenReturn(List.of());
        KeyMaterial v1 = new KeyMaterial(key, 1, KeyState.PRE_ACTIVE, "W", "IV", null, Instant.now());
        machine.recordCreated(v1, HistoryTrigger.OPERATION, "등록", "admin");
        assertEquals(KeyState.PRE_ACTIVE, key.getStatus());
        verify(historyRepository).save(any(KeyStatusHistory.class));
    }
}
