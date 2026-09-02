package com.ineb.kms.key;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ineb.kms.audit.AuditHook;
import com.ineb.kms.crypto.MasterKeyHolder;
import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.HistoryTrigger;
import com.ineb.kms.domain.KeyAlgorithm;
import com.ineb.kms.domain.KeyMaterial;
import com.ineb.kms.domain.KeyMode;
import com.ineb.kms.domain.KeyState;
import com.ineb.kms.key.dto.KeyActionRequest;
import com.ineb.kms.domain.KeyAction;
import com.ineb.kms.key.dto.KeyCreateRequest;
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

class KeyLifecycleSchedulerTest {

    private KeyService keyService;
    private KeyOperationService ops;
    private KeyLifecycleScheduler scheduler;
    private KeyIntegrityHasher hasher;
    private final List<KeyMaterial> materials = new ArrayList<>();
    private final List<CryptoKey> keys = new ArrayList<>();
    private final List<String> audits = new ArrayList<>();

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
        KeyLifecycleWorker worker = new KeyLifecycleWorker(materialRepository, keyRepository, machine, ops, hasher, guard);
        scheduler = new KeyLifecycleScheduler(materialRepository, keyRepository, worker, true, true);

        when(keyRepository.save(any(CryptoKey.class))).thenAnswer(inv -> {
            CryptoKey k = inv.getArgument(0);
            setId(k, (long) (keys.size() + 1));
            keys.add(k);
            when(keyRepository.findByKeyUid(k.getKeyUid())).thenReturn(Optional.of(k));
            when(keyRepository.findById(k.getId())).thenReturn(Optional.of(k));
            return k;
        });
        when(materialRepository.save(any(KeyMaterial.class))).thenAnswer(inv -> {
            KeyMaterial m = inv.getArgument(0);
            setId(m, (long) (materials.size() + 100));
            materials.add(m);
            when(materialRepository.findById(m.getId())).thenReturn(Optional.of(m));
            return m;
        });
        when(materialRepository.findByKeyIdOrderByVersionDesc(anyLong())).thenAnswer(inv -> materials.stream()
                .filter(m -> m.getKey().getId().equals(inv.getArgument(0)))
                .sorted((a, b) -> b.getVersion() - a.getVersion()).toList());
        when(materialRepository.findByKeyIdAndVersion(anyLong(), anyInt())).thenAnswer(inv -> materials.stream()
                .filter(m -> m.getKey().getId().equals(inv.getArgument(0)) && m.getVersion() == (int) inv.getArgument(1)).findFirst());
        when(materialRepository.findByKeyIdAndState(anyLong(), any(KeyState.class))).thenAnswer(inv -> materials.stream()
                .filter(m -> m.getKey().getId().equals(inv.getArgument(0)) && m.getState() == inv.getArgument(1)).toList());
        when(materialRepository.findByStateAndActivationDateLessThanEqual(any(KeyState.class), any(Instant.class)))
                .thenAnswer(inv -> materials.stream().filter(m -> m.getState() == inv.getArgument(0)
                        && !m.getActivationDate().isAfter(inv.getArgument(1))).toList());
        when(materialRepository.findByStateNot(any(KeyState.class))).thenAnswer(inv ->
                materials.stream().filter(m -> m.getState() != inv.getArgument(0)).toList());
        when(keyRepository.findByAutoRotateTrueAndNextRotationAtLessThanEqualAndStatus(any(Instant.class), any(KeyState.class)))
                .thenAnswer(inv -> keys.stream().filter(k -> k.isAutoRotate() && k.getStatus() == inv.getArgument(1)
                        && k.getNextRotationAt() != null && !k.getNextRotationAt().isAfter(inv.getArgument(0))).toList());
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

    private CryptoKey create(String name, boolean autoRotate, String activationDate) {
        String uid = keyService.create(new KeyCreateRequest(name, KeyAlgorithm.AES, 256, KeyMode.GCM, null,
                autoRotate, 90, activationDate, null), "admin").keyUid();
        return keys.stream().filter(k -> k.getKeyUid().equals(uid)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("활성일이 도래한 PRE_ACTIVE 버전은 스케줄러가 ACTIVE 로 전이하고 current 를 교체한다")
    void activatesDueVersions() {
        CryptoKey k = create("K1", false, "2099-01-01 00:00");
        assertEquals(KeyState.PRE_ACTIVE, k.getStatus());
        assertEquals(0, scheduler.activateDue(Instant.now()));                 // 아직 미도래

        int activated = scheduler.activateDue(Instant.parse("2099-01-02T00:00:00Z"));
        assertEquals(1, activated);
        assertEquals(KeyState.ACTIVE, k.getStatus());
        assertEquals(1, k.getCurrentVersion());
        assertTrue(hasher.verify(k));
    }

    @Test
    @DisplayName("예약 갱신 버전이 도래하면 활성되며 최신 버전(current)이 되고 구 버전은 ACTIVE 로 남는다")
    void activatesScheduledRotation() {
        CryptoKey k = create("K2", false, null);
        ops.execute(k.getKeyUid(), new KeyActionRequest(KeyAction.ROTATE, "예약", "2099-01-01 00:00", null), "admin");
        assertEquals(1, k.getCurrentVersion());

        assertEquals(1, scheduler.activateDue(Instant.parse("2099-01-01T00:00:01Z")));
        assertEquals(2, k.getCurrentVersion());
        assertTrue(materials.stream().allMatch(m -> m.getState() == KeyState.ACTIVE));
    }

    @Test
    @DisplayName("갱신 주기가 도래한 자동 갱신 키는 SCHEDULE 트리거로 회전되고 다음 갱신일이 미뤄진다")
    void rotatesDueKeys() {
        CryptoKey k = create("K3", true, null);
        Instant firstDue = k.getNextRotationAt();
        assertEquals(0, scheduler.rotateDue(Instant.now()));

        assertEquals(1, scheduler.rotateDue(firstDue.plusSeconds(1)));
        assertEquals(2, k.getCurrentVersion());
        assertTrue(k.getNextRotationAt().isAfter(firstDue));
        assertTrue(audits.stream().anyMatch(a -> a.startsWith("KEY_ROTATED") && a.contains("trigger=" + HistoryTrigger.SCHEDULE)));

        // 다음 갱신일 전에는 다시 갱신되지 않는다
        assertEquals(0, scheduler.rotateDue(k.getNextRotationAt().minusSeconds(1)));
        assertEquals(2, k.getCurrentVersion());
    }

    @Test
    @DisplayName("정지된 키·수동 갱신 키는 자동 갱신 대상이 아니다")
    void skipsInactiveOrManual() {
        CryptoKey auto = create("K4", true, null);
        CryptoKey manual = create("K5", false, null);
        ops.execute(auto.getKeyUid(), new KeyActionRequest(KeyAction.DEACTIVATE, "정지", null, null), "admin");
        assertFalse(auto.isAutoRotate());
        assertEquals(0, scheduler.rotateDue(Instant.now().plusSeconds(100L * 86_400)));
        assertEquals(1, manual.getCurrentVersion());
    }

    @Test
    @DisplayName("무결성 배치는 변조된 버전만 자동 정지한다")
    void integritySweep() {
        CryptoKey k = create("K6", false, null);
        create("K7", false, null);
        materials.get(0).rescheduleActivation(Instant.now().minusSeconds(5));   // 해시 재계산 없이 변조
        assertEquals(1, scheduler.sweep());
        assertEquals(KeyState.DEACTIVATED, materials.get(0).getState());
        assertEquals(KeyState.DEACTIVATED, k.getStatus());
        assertEquals(KeyState.ACTIVE, materials.get(1).getState());
        assertEquals(0, scheduler.sweep());
    }

    @Test
    @DisplayName("enabled=false 면 tick 이 아무 일도 하지 않는다")
    void disabled() {
        KeyLifecycleScheduler off = new KeyLifecycleScheduler(mock(KeyMaterialRepository.class),
                mock(CryptoKeyRepository.class), mock(KeyLifecycleWorker.class), false, true);
        off.tick();
    }
}
