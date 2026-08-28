package com.ineb.kms.key;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.crypto.MasterKeyHolder;
import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.DeactivationTrigger;
import com.ineb.kms.domain.HistoryTrigger;
import com.ineb.kms.domain.KeyAction;
import com.ineb.kms.domain.KeyAlgorithm;
import com.ineb.kms.domain.KeyMaterial;
import com.ineb.kms.domain.KeyMode;
import com.ineb.kms.domain.KeyState;
import com.ineb.kms.key.dto.KeyActionRequest;
import com.ineb.kms.key.dto.KeyCreateRequest;
import com.ineb.kms.key.dto.KeyDetail;
import com.ineb.kms.repository.CryptoKeyRepository;
import com.ineb.kms.repository.KeyMaterialRepository;
import com.ineb.kms.repository.KeyStatusHistoryRepository;
import com.ineb.kms.repository.KeyUsageLogRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeyOperationServiceTest {

    private KeyService keyService;
    private KeyOperationService ops;
    private KeyIntegrityHasher hasher;
    private final List<KeyMaterial> materials = new ArrayList<>();
    private final List<String> audits = new ArrayList<>();
    private CryptoKey key;

    @BeforeEach
    void setUp() {
        CryptoKeyRepository keyRepository = mock(CryptoKeyRepository.class);
        KeyMaterialRepository materialRepository = mock(KeyMaterialRepository.class);
        KeyUsageLogRepository usageLogRepository = mock(KeyUsageLogRepository.class);
        MasterKeyHolder holder = mock(MasterKeyHolder.class);
        when(holder.getKey()).thenReturn(new byte[32]);

        hasher = new KeyIntegrityHasher(new byte[32]);
        KeyStateMachine machine = new KeyStateMachine(materialRepository, mock(KeyStatusHistoryRepository.class), hasher);
        AuditHook audit = (actor, action, target, detail) -> audits.add(action + ":" + detail);
        KeyIntegrityGuard guard = new KeyIntegrityGuard(hasher, machine, materialRepository, audit);
        KeyMaterialFactory factory = new KeyMaterialFactory(holder);
        keyService = new KeyService(keyRepository, materialRepository, usageLogRepository,
                mock(KeyStatusHistoryRepository.class), factory, machine, hasher, guard, audit);
        ops = new KeyOperationService(keyService, materialRepository, factory, machine, hasher, audit);

        when(keyRepository.save(any(CryptoKey.class))).thenAnswer(inv -> {
            CryptoKey k = inv.getArgument(0);
            setId(k, 1L);
            key = k;
            when(keyRepository.findByKeyUid(k.getKeyUid())).thenReturn(Optional.of(k));
            return k;
        });
        when(materialRepository.save(any(KeyMaterial.class))).thenAnswer(inv -> {
            KeyMaterial m = inv.getArgument(0);
            setId(m, 100L + m.getVersion());
            materials.add(m);
            return m;
        });
        when(materialRepository.findByKeyIdOrderByVersionDesc(anyLong())).thenAnswer(inv ->
                materials.stream().sorted((a, b) -> b.getVersion() - a.getVersion()).toList());
        when(materialRepository.findByKeyIdAndVersion(anyLong(), anyInt())).thenAnswer(inv ->
                materials.stream().filter(m -> m.getVersion() == (int) inv.getArgument(1)).findFirst());
        when(materialRepository.findByKeyIdAndState(anyLong(), any(KeyState.class))).thenAnswer(inv ->
                materials.stream().filter(m -> m.getState() == inv.getArgument(1)).toList());
        when(usageLogRepository.findTopByKeyIdAndVersionOrderByUsedAtDesc(anyLong(), anyInt())).thenReturn(Optional.empty());
        when(keyRepository.existsByKeyNameAndIdNot(anyString(), anyLong())).thenReturn(false);
    }

    private static void setId(Object entity, Long id) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private String createActive() {
        return keyService.create(new KeyCreateRequest("K", KeyAlgorithm.AES, 256, KeyMode.GCM, null,
                true, 90, null, null), "admin").keyUid();
    }

    private KeyDetail run(String uid, KeyAction action, Integer version, String activationDate) {
        return ops.execute(uid, new KeyActionRequest(action, "테스트 사유", activationDate, version), "admin");
    }

    private KeyMaterial v(int version) {
        return materials.stream().filter(m -> m.getVersion() == version).findFirst().orElseThrow();
    }

    private static ErrorCode fail(Runnable r) {
        return assertThrows(BusinessException.class, r::run).getErrorCode();
    }

    @Test
    @DisplayName("갱신하면 새 버전이 최신 ACTIVE 가 되고 구 버전은 ACTIVE 로 남는다 (복호화 전용)")
    void rotateKeepsOldActive() {
        String uid = createActive();
        KeyDetail d = run(uid, KeyAction.ROTATE, null, null);

        assertEquals(2, d.currentVersion());
        assertEquals(2, d.versions().size());
        assertEquals("ACTIVE", v(1).getState().name());
        assertEquals("ACTIVE", v(2).getState().name());
        assertTrue(d.versions().stream().filter(x -> x.version() == 2).findFirst().orElseThrow().canEncrypt());
        assertFalse(d.versions().stream().filter(x -> x.version() == 1).findFirst().orElseThrow().canEncrypt());
        assertTrue(d.versions().stream().filter(x -> x.version() == 1).findFirst().orElseThrow().canDecrypt());
        assertTrue(hasher.verify(key) && hasher.verify(v(1)) && hasher.verify(v(2)));
        assertTrue(audits.stream().anyMatch(a -> a.startsWith("KEY_ROTATED:newVersion=2")));
    }

    @Test
    @DisplayName("미래 활성일로 갱신하면 PRE_ACTIVE 예약이 되고 current 는 그대로다. 다시 갱신하면 예약이 취소된다")
    void rotateScheduled() {
        String uid = createActive();
        KeyDetail d = run(uid, KeyAction.ROTATE, null, "2099-01-01 00:00");
        assertEquals(1, d.currentVersion());
        assertEquals("PRE_ACTIVE", v(2).getState().name());

        run(uid, KeyAction.ROTATE, null, null);
        assertEquals("DESTROYED", v(2).getState().name());
        assertNull(v(2).getWrappedKey());
        assertEquals("ACTIVE", v(3).getState().name());
        assertEquals(3, key.getCurrentVersion());
    }

    @Test
    @DisplayName("예약된 버전을 ACTIVATE 하면 즉시 최신 버전이 된다")
    void activateScheduled() {
        String uid = createActive();
        run(uid, KeyAction.ROTATE, null, "2099-01-01 00:00");
        KeyDetail d = run(uid, KeyAction.ACTIVATE, null, null);
        assertEquals(2, d.currentVersion());
        assertEquals("ACTIVE", v(2).getState().name());
        assertTrue(v(2).getActivationDate().isBefore(Instant.now().plusSeconds(1)));
    }

    @Test
    @DisplayName("구 버전은 단독 정지할 수 있지만 최신 버전은 409 다")
    void deactivateVersion() {
        String uid = createActive();
        run(uid, KeyAction.ROTATE, null, null);
        assertEquals(ErrorCode.KEY_LATEST_VERSION_DEACTIVATE, fail(() -> run(uid, KeyAction.DEACTIVATE, 2, null)));

        run(uid, KeyAction.DEACTIVATE, 1, null);
        assertEquals(KeyState.DEACTIVATED, v(1).getState());
        assertEquals(DeactivationTrigger.OPERATION, v(1).getDeactivationTrigger());
        assertEquals(KeyState.ACTIVE, key.getStatus());
        assertEquals(ErrorCode.KEY_STATE_CONFLICT, fail(() -> run(uid, KeyAction.DEACTIVATE, 1, null)));
    }

    @Test
    @DisplayName("키 전체 정지는 모든 ACTIVE 버전을 정지하고 자동 갱신을 중단한다")
    void deactivateKey() {
        String uid = createActive();
        run(uid, KeyAction.ROTATE, null, null);
        KeyDetail d = run(uid, KeyAction.DEACTIVATE, null, null);
        assertEquals("DEACTIVATED", d.status());
        assertFalse(d.autoRotate());
        assertEquals(KeyState.DEACTIVATED, v(1).getState());
        assertEquals(KeyState.DEACTIVATED, v(2).getState());
        assertTrue(hasher.verify(key));
    }

    @Test
    @DisplayName("관리자가 정지한 버전은 재활성화할 수 없고, 무결성 정지 버전은 언래핑 검증 후 복구된다")
    void reactivate() {
        String uid = createActive();
        run(uid, KeyAction.ROTATE, null, null);
        run(uid, KeyAction.DEACTIVATE, 1, null);
        assertEquals(ErrorCode.KEY_REACTIVATE_NOT_ALLOWED, fail(() -> run(uid, KeyAction.REACTIVATE, 1, null)));
        assertEquals(ErrorCode.INVALID_INPUT, fail(() -> run(uid, KeyAction.REACTIVATE, null, null)));

        // 무결성 위반 시나리오: v2(최신)를 INTEGRITY 로 정지시켜 둔다
        v(2).transition(KeyState.DEACTIVATED, DeactivationTrigger.INTEGRITY);
        key.recalcStatus(materials);
        KeyDetail d = run(uid, KeyAction.REACTIVATE, 2, null);
        assertEquals("ACTIVE", v(2).getState().name());
        assertNull(v(2).getDeactivationTrigger());
        assertEquals(2, d.currentVersion());
        assertTrue(hasher.verify(v(2)));
        assertTrue(audits.stream().anyMatch(a -> a.startsWith("KEY_REACTIVATED:version=2")));
    }

    @Test
    @DisplayName("재료가 손상된 버전은 재활성화가 409 로 거부된다")
    void reactivateCorrupted() throws Exception {
        String uid = createActive();
        run(uid, KeyAction.ROTATE, null, null);
        KeyMaterial m = v(1);
        m.transition(KeyState.DEACTIVATED, DeactivationTrigger.INTEGRITY);
        Field f = KeyMaterial.class.getDeclaredField("wrappedKey");
        f.setAccessible(true);
        f.set(m, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        assertEquals(ErrorCode.KEY_MATERIAL_CORRUPTED, fail(() -> run(uid, KeyAction.REACTIVATE, 1, null)));
        assertEquals(KeyState.DEACTIVATED, m.getState());
    }

    @Test
    @DisplayName("ACTIVE 버전은 삭제할 수 없고, 정지된 버전 삭제 시 재료가 파기되고 이력이 남는다")
    void destroyVersion() {
        String uid = createActive();
        run(uid, KeyAction.ROTATE, null, null);
        assertEquals(ErrorCode.KEY_STATE_CONFLICT, fail(() -> run(uid, KeyAction.DESTROY, 1, null)));

        run(uid, KeyAction.DEACTIVATE, 1, null);
        KeyDetail d = run(uid, KeyAction.DESTROY, 1, null);
        assertEquals(KeyState.DESTROYED, v(1).getState());
        assertNull(v(1).getWrappedKey());
        assertFalse(v(1).isMaterialAvailable());
        assertEquals("ACTIVE", d.status());
        assertEquals(2, d.versions().size());          // 행은 보존
        assertTrue(hasher.verify(v(1)));                // NULL 반영 해시
    }

    @Test
    @DisplayName("ACTIVE 버전이 있으면 키 전체 삭제는 409, 키 정지 후에는 전 버전이 파기되고 키가 DESTROYED 가 된다")
    void destroyKey() {
        String uid = createActive();
        run(uid, KeyAction.ROTATE, null, null);
        assertEquals(ErrorCode.KEY_ACTIVE_EXISTS, fail(() -> run(uid, KeyAction.DESTROY, null, null)));

        run(uid, KeyAction.DEACTIVATE, null, null);
        KeyDetail d = run(uid, KeyAction.DESTROY, null, null);
        assertEquals("DESTROYED", d.status());
        assertTrue(materials.stream().allMatch(m -> m.getState() == KeyState.DESTROYED));
        assertEquals(ErrorCode.KEY_STATE_CONFLICT, fail(() -> run(uid, KeyAction.ROTATE, null, null)));
    }

    @Test
    @DisplayName("버전 상한(100)에 도달하면 갱신이 400 으로 거부된다")
    void versionLimit() {
        String uid = createActive();
        for (int i = 2; i <= CryptoKey.MAX_VERSIONS; i++) {
            run(uid, KeyAction.ROTATE, null, null);
        }
        assertEquals(CryptoKey.MAX_VERSIONS, materials.size());
        assertEquals(ErrorCode.KEY_VERSION_LIMIT, fail(() -> run(uid, KeyAction.ROTATE, null, null)));
    }

    @Test
    @DisplayName("스케줄 갱신은 다음 갱신일을 주기만큼 미루고 SCHEDULE 트리거를 남긴다")
    void scheduledRotate() {
        createActive();
        Instant before = key.getNextRotationAt();
        ops.rotate(key, null, "회전 주기 도래", "SYSTEM", HistoryTrigger.SCHEDULE);
        assertEquals(2, key.getCurrentVersion());
        assertTrue(key.getNextRotationAt().isAfter(before.minusSeconds(1)));
        assertTrue(audits.stream().anyMatch(a -> a.contains("trigger=SCHEDULE")));
    }
}
