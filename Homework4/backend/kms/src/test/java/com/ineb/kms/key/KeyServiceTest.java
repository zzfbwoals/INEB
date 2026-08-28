package com.ineb.kms.key;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.ineb.kms.domain.KeyAlgorithm;
import com.ineb.kms.domain.KeyMaterial;
import com.ineb.kms.domain.KeyMode;
import com.ineb.kms.domain.KeyPurpose;
import com.ineb.kms.domain.KeyState;
import com.ineb.kms.key.dto.KeyCreateRequest;
import com.ineb.kms.key.dto.KeyDetail;
import com.ineb.kms.key.dto.KeyUpdateRequest;
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

class KeyServiceTest {

    private CryptoKeyRepository keyRepository;
    private KeyMaterialRepository materialRepository;
    private KeyUsageLogRepository usageLogRepository;
    private KeyService service;

    private final List<KeyMaterial> materials = new ArrayList<>();
    private final List<String> audits = new ArrayList<>();

    @BeforeEach
    void setUp() {
        keyRepository = mock(CryptoKeyRepository.class);
        materialRepository = mock(KeyMaterialRepository.class);
        usageLogRepository = mock(KeyUsageLogRepository.class);
        MasterKeyHolder holder = mock(MasterKeyHolder.class);
        when(holder.getKey()).thenReturn(new byte[32]);

        KeyIntegrityHasher hasher = new KeyIntegrityHasher(new byte[32]);
        KeyStateMachine machine = new KeyStateMachine(materialRepository, mock(KeyStatusHistoryRepository.class), hasher);
        AuditHook audit = (actor, action, target, detail) -> audits.add(action);
        KeyIntegrityGuard guard = new KeyIntegrityGuard(hasher, machine, materialRepository, audit);
        service = new KeyService(keyRepository, materialRepository, usageLogRepository,
                mock(KeyStatusHistoryRepository.class), new KeyMaterialFactory(holder), machine, hasher, guard, audit);

        // 저장 시 id 부여, 목록 조회는 메모리 리스트
        when(keyRepository.save(any(CryptoKey.class))).thenAnswer(inv -> {
            CryptoKey k = inv.getArgument(0);
            setId(k, 1L);
            return k;
        });
        when(materialRepository.save(any(KeyMaterial.class))).thenAnswer(inv -> {
            KeyMaterial m = inv.getArgument(0);
            setId(m, 100L + m.getVersion());
            materials.add(m);
            return m;
        });
        when(materialRepository.findByKeyIdOrderByVersionDesc(anyLong())).thenReturn(materials);
        when(materialRepository.findByKeyIdAndVersion(anyLong(), anyInt())).thenAnswer(inv ->
                materials.stream().filter(m -> m.getVersion() == (int) inv.getArgument(1)).findFirst());
        when(usageLogRepository.findTopByKeyIdAndVersionOrderByUsedAtDesc(anyLong(), anyInt())).thenReturn(Optional.empty());
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

    private KeyCreateRequest request(KeyAlgorithm alg, int size, KeyMode mode, String activationDate) {
        return new KeyCreateRequest("PAY-GW", alg, size, mode, null, true, 90, activationDate, "설명");
    }

    @Test
    @DisplayName("활성일 없이 등록하면 v1 이 즉시 ACTIVE 로 생성되고 갱신 정책·해시·감사가 기록된다")
    void createImmediate() {
        KeyDetail d = service.create(request(KeyAlgorithm.AES, 256, KeyMode.GCM, null), "admin");

        assertEquals("ACTIVE", d.status());
        assertEquals(1, d.currentVersion());
        assertEquals(1, d.versions().size());
        assertEquals("ACTIVE", d.versions().get(0).state());
        assertTrue(d.versions().get(0).canEncrypt());
        assertTrue(d.autoRotate());
        assertEquals(90, d.rotationPeriodDays());
        assertNotNull(d.nextRotationAt());
        assertEquals("ENC_DEC", d.purpose());
        assertNull(d.publicKeyPem());
        assertTrue(d.integrityValid());
        assertTrue(d.versions().get(0).integrityValid());
        assertEquals(List.of("KEY_CREATED"), audits);
    }

    @Test
    @DisplayName("미래 활성일로 등록하면 PRE_ACTIVE 로 생성된다")
    void createPreActive() {
        KeyDetail d = service.create(request(KeyAlgorithm.ARIA, 128, KeyMode.CBC, "2099-01-01 00:00"), "admin");
        assertEquals("PRE_ACTIVE", d.status());
        assertEquals("2099-01-01 00:00:00", d.versions().get(0).activationDate());
        assertFalse(d.versions().get(0).canEncrypt());
    }

    @Test
    @DisplayName("RSA 는 용도가 자동으로 결정되고 공개키 PEM 이 상세에 포함된다")
    void createRsa() {
        KeyDetail d = service.create(request(KeyAlgorithm.RSA, 2048, null, null), "admin");
        assertEquals("ENC_DEC_SIGN_VERIFY", d.purpose());
        assertTrue(d.publicKeyPem().startsWith("-----BEGIN PUBLIC KEY-----"));
        assertNull(d.mode());
    }

    @Test
    @DisplayName("사이즈·모드·용도 조합이 알고리즘 규칙과 맞지 않으면 400 이다")
    void invalidParams() {
        assertEquals(ErrorCode.KEY_INVALID_ALGORITHM_PARAM, fail(() ->
                service.create(request(KeyAlgorithm.SEED, 256, KeyMode.CBC, null), "admin")));
        assertEquals(ErrorCode.KEY_INVALID_ALGORITHM_PARAM, fail(() ->
                service.create(request(KeyAlgorithm.SEED, 128, KeyMode.GCM, null), "admin")));
        assertEquals(ErrorCode.KEY_INVALID_ALGORITHM_PARAM, fail(() ->
                service.create(request(KeyAlgorithm.RSA, 2048, KeyMode.CBC, null), "admin")));
        assertEquals(ErrorCode.KEY_INVALID_ALGORITHM_PARAM, fail(() -> service.create(
                new KeyCreateRequest("K", KeyAlgorithm.AES, 256, KeyMode.GCM, KeyPurpose.SIGN_VERIFY, true, 90, null, null), "admin")));
    }

    @Test
    @DisplayName("갱신 주기는 1~730 이어야 하고 키명은 중복될 수 없다")
    void rotationAndName() {
        assertEquals(ErrorCode.KEY_ROTATION_PERIOD_INVALID, fail(() -> service.create(
                new KeyCreateRequest("K", KeyAlgorithm.AES, 256, KeyMode.GCM, null, true, 731, null, null), "admin")));
        when(keyRepository.existsByKeyName("DUP")).thenReturn(true);
        assertEquals(ErrorCode.KEY_NAME_DUPLICATE, fail(() -> service.create(
                new KeyCreateRequest("DUP", KeyAlgorithm.AES, 256, KeyMode.GCM, null, false, null, null, null), "admin")));
    }

    @Test
    @DisplayName("수정 시 키명·설명·갱신 정책이 반영되고 해시가 재계산된다")
    void update() {
        KeyDetail created = service.create(request(KeyAlgorithm.AES, 256, KeyMode.GCM, null), "admin");
        CryptoKey key = keyOf(created);
        String before = key.getIntegrityHash();

        KeyDetail d = service.update(created.keyUid(),
                new KeyUpdateRequest("PAY-GW-2", "new", false, null, null), "admin");
        assertEquals("PAY-GW-2", d.keyName());
        assertFalse(d.autoRotate());
        assertNull(d.nextRotationAt().isEmpty() ? null : d.nextRotationAt());
        assertFalse(before.equals(key.getIntegrityHash()));
        assertTrue(audits.contains("KEY_UPDATED"));
    }

    @Test
    @DisplayName("PRE_ACTIVE 현행 버전의 활성일을 과거로 수정하면 즉시 ACTIVE 가 되고, ACTIVE 버전은 수정할 수 없다")
    void updateActivationDate() {
        KeyDetail created = service.create(request(KeyAlgorithm.AES, 256, KeyMode.GCM, "2099-01-01 00:00"), "admin");
        keyOf(created);
        KeyDetail d = service.update(created.keyUid(),
                new KeyUpdateRequest("PAY-GW", null, true, 90, "2020-01-01 00:00"), "admin");
        assertEquals("ACTIVE", d.status());
        assertEquals("ACTIVE", d.versions().get(0).state());

        assertEquals(ErrorCode.KEY_ACTIVATION_DATE_NOT_EDITABLE, fail(() -> service.update(created.keyUid(),
                new KeyUpdateRequest("PAY-GW", null, true, 90, "2099-01-01 00:00"), "admin")));
    }

    private CryptoKey keyOf(KeyDetail created) {
        CryptoKey key = materials.get(0).getKey();
        when(keyRepository.findByKeyUid(created.keyUid())).thenReturn(Optional.of(key));
        when(keyRepository.existsByKeyNameAndIdNot(anyString(), anyLong())).thenReturn(false);
        return key;
    }

    private static ErrorCode fail(Runnable r) {
        return assertThrows(BusinessException.class, r::run).getErrorCode();
    }
}
