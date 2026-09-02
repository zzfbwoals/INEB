package com.ineb.kms.key;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ineb.kms.audit.AuditHook;
import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.crypto.MasterKeyHolder;
import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.KeyAction;
import com.ineb.kms.domain.KeyAlgorithm;
import com.ineb.kms.domain.KeyMaterial;
import com.ineb.kms.domain.KeyMode;
import com.ineb.kms.domain.KeyState;
import com.ineb.kms.domain.KeyUsageLog;
import com.ineb.kms.domain.UsageResult;
import com.ineb.kms.key.dto.CipherResponse;
import com.ineb.kms.key.dto.KeyActionRequest;
import com.ineb.kms.key.dto.KeyCreateRequest;
import com.ineb.kms.key.dto.PlainResponse;
import com.ineb.kms.key.dto.SignResponse;
import com.ineb.kms.key.dto.VerifyResponse;
import com.ineb.kms.repository.CryptoKeyRepository;
import com.ineb.kms.repository.KeyMaterialRepository;
import com.ineb.kms.repository.KeyStatusHistoryRepository;
import com.ineb.kms.repository.KeyUsageLogRepository;
import java.lang.reflect.Field;
import java.security.Security;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeyTestServiceTest {

    private static final String PLAIN = "고객 카드번호 테스트 데이터 4111-1111-1111-1111";

    private KeyService keyService;
    private KeyOperationService ops;
    private KeyTestService tests;
    private final List<KeyMaterial> materials = new ArrayList<>();
    private final List<KeyUsageLog> logs = new ArrayList<>();
    private final List<String> audits = new ArrayList<>();

    @BeforeAll
    static void bc() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @BeforeEach
    void setUp() {
        CryptoKeyRepository keyRepository = mock(CryptoKeyRepository.class);
        KeyMaterialRepository materialRepository = mock(KeyMaterialRepository.class);
        KeyUsageLogRepository usageLogRepository = mock(KeyUsageLogRepository.class);
        MasterKeyHolder holder = mock(MasterKeyHolder.class);
        when(holder.getKey()).thenReturn(new byte[32]);

        KeyIntegrityHasher hasher = new KeyIntegrityHasher(new byte[32]);
        KeyStateMachine machine = new KeyStateMachine(materialRepository, mock(KeyStatusHistoryRepository.class), hasher);
        AuditHook audit = (actor, action, target, detail) -> audits.add(action + ":" + detail);
        KeyIntegrityGuard guard = new KeyIntegrityGuard(hasher, machine, materialRepository, audit);
        KeyMaterialFactory factory = new KeyMaterialFactory(holder);
        keyService = new KeyService(keyRepository, materialRepository, usageLogRepository,
                mock(KeyStatusHistoryRepository.class), factory, machine, hasher, guard, audit);
        ops = new KeyOperationService(keyService, materialRepository, factory, machine, hasher, audit);
        tests = new KeyTestService(keyService, materialRepository, usageLogRepository, factory, guard, audit);

        when(keyRepository.save(any(CryptoKey.class))).thenAnswer(inv -> {
            CryptoKey k = inv.getArgument(0);
            setId(k, 1L);
            when(keyRepository.findByKeyUid(k.getKeyUid())).thenReturn(Optional.of(k));
            return k;
        });
        when(materialRepository.save(any(KeyMaterial.class))).thenAnswer(inv -> {
            KeyMaterial m = inv.getArgument(0);
            setId(m, 100L + m.getVersion());
            materials.add(m);
            return m;
        });
        when(usageLogRepository.save(any(KeyUsageLog.class))).thenAnswer(inv -> {
            logs.add(inv.getArgument(0));
            return inv.getArgument(0);
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

    private String create(KeyAlgorithm alg, int size, KeyMode mode) {
        return keyService.create(new KeyCreateRequest("K-" + alg, alg, size, mode, null, false, null, null, null), "admin").keyUid();
    }

    private void act(String uid, KeyAction action, Integer version) {
        ops.execute(uid, new KeyActionRequest(action, "사유", null, version), "admin");
    }

    private static ErrorCode fail(Runnable r) {
        return assertThrows(BusinessException.class, r::run).getErrorCode();
    }

    private KeyUsageLog lastLog() {
        return logs.get(logs.size() - 1);
    }

    @Test
    @DisplayName("AES-GCM 암호화 → 복호화 라운드트립, 암호문은 version 접두를 가지며 usage_log 가 기록된다")
    void encryptDecryptRoundTrip() {
        String uid = create(KeyAlgorithm.AES, 256, KeyMode.GCM);
        CipherResponse c = tests.encrypt(uid, PLAIN, "admin");
        assertTrue(c.ciphertext().startsWith("1:"));
        assertEquals(1, c.version());

        PlainResponse p = tests.decrypt(uid, c.ciphertext(), "admin");
        assertEquals(PLAIN, p.plaintext());
        assertFalse(p.oldVersion());
        assertEquals(2, logs.size());
        assertTrue(logs.stream().allMatch(l -> l.getResult() == UsageResult.SUCCESS));
        assertTrue(audits.contains("KEY_TEST_ENCRYPT:version=1"));
    }

    @Test
    @DisplayName("갱신 후 구 버전 암호문은 구 버전으로 복호화되고(oldVersion=true), 새 암호문은 v2 로 만들어진다")
    void decryptWithOldVersionAfterRotate() {
        String uid = create(KeyAlgorithm.ARIA, 256, KeyMode.CBC);
        String v1ct = tests.encrypt(uid, PLAIN, "admin").ciphertext();
        act(uid, KeyAction.ROTATE, null);

        assertTrue(tests.encrypt(uid, PLAIN, "admin").ciphertext().startsWith("2:"));
        PlainResponse p = tests.decrypt(uid, v1ct, "admin");
        assertEquals(PLAIN, p.plaintext());
        assertEquals(1, p.version());
        assertTrue(p.oldVersion());
    }

    @Test
    @DisplayName("정지된 버전으로는 복호화가 차단되고 실패가 usage_log 에 남는다")
    void deactivatedVersionBlocked() {
        String uid = create(KeyAlgorithm.LEA, 128, KeyMode.CTR);
        String v1ct = tests.encrypt(uid, PLAIN, "admin").ciphertext();
        act(uid, KeyAction.ROTATE, null);
        act(uid, KeyAction.DEACTIVATE, 1);

        assertEquals(ErrorCode.KEY_VERSION_NOT_USABLE, fail(() -> tests.decrypt(uid, v1ct, "admin")));
        assertEquals(UsageResult.FAIL, lastLog().getResult());
        assertEquals(1, lastLog().getVersion());
    }

    @Test
    @DisplayName("존재하지 않는 버전·형식 오류 암호문은 400 이다")
    void badCiphertext() {
        String uid = create(KeyAlgorithm.SEED, 128, KeyMode.CBC);
        assertEquals(ErrorCode.KEY_VERSION_NOT_FOUND, fail(() -> tests.decrypt(uid, "9:aa:bb", "admin")));
        assertEquals(ErrorCode.KEY_CIPHERTEXT_FORMAT, fail(() -> tests.decrypt(uid, "garbage", "admin")));
    }

    @Test
    @DisplayName("무결성이 변조된 버전은 언래핑 직전 자동 정지되고 409 를 던지며 실패 로그가 남는다")
    void integrityViolationOnUse() {
        String uid = create(KeyAlgorithm.AES, 128, KeyMode.CBC);
        materials.get(0).rescheduleActivation(Instant.now().plusSeconds(60));   // 해시 재계산 없이 변조
        assertEquals(ErrorCode.KEY_INTEGRITY_VIOLATION, fail(() -> tests.encrypt(uid, PLAIN, "admin")));
        assertEquals(KeyState.DEACTIVATED, materials.get(0).getState());
        assertEquals(UsageResult.FAIL, lastLog().getResult());
        assertTrue(audits.stream().anyMatch(a -> a.startsWith("KEY_INTEGRITY_VIOLATION")));
        // 이후 암호화도 차단 (current 가 ACTIVE 아님)
        assertEquals(ErrorCode.KEY_VERSION_NOT_USABLE, fail(() -> tests.encrypt(uid, PLAIN, "admin")));
    }

    @Test
    @DisplayName("RSA 는 암복호화와 서명·검증 모두 가능하고 평문 상한(190B)을 초과하면 400 이다")
    void rsaEncryptAndSign() {
        String uid = create(KeyAlgorithm.RSA, 2048, null);
        CipherResponse c = tests.encrypt(uid, PLAIN, "admin");
        assertTrue(c.ciphertext().startsWith("1::"));          // IV 없음
        assertEquals(PLAIN, tests.decrypt(uid, c.ciphertext(), "admin").plaintext());

        SignResponse s = tests.sign(uid, PLAIN, "admin");
        assertTrue(tests.verify(uid, PLAIN, s.signature(), "admin").valid());
        assertFalse(tests.verify(uid, PLAIN + "x", s.signature(), "admin").valid());
        assertEquals(UsageResult.FAIL, lastLog().getResult());

        assertEquals(ErrorCode.KEY_PLAINTEXT_TOO_LONG, fail(() -> tests.encrypt(uid, "a".repeat(191), "admin")));
    }

    @Test
    @DisplayName("ECDSA 는 서명·검증만 가능하고 암호화는 용도 불일치 400 이다")
    void ecdsaSignOnly() {
        String uid = create(KeyAlgorithm.ECDSA, 256, null);
        assertEquals(ErrorCode.KEY_PURPOSE_MISMATCH, fail(() -> tests.encrypt(uid, PLAIN, "admin")));
        SignResponse s = tests.sign(uid, PLAIN, "admin");
        VerifyResponse v = tests.verify(uid, PLAIN, s.signature(), "admin");
        assertTrue(v.valid());
        assertEquals(1, v.version());
    }

    @Test
    @DisplayName("HMAC 키는 MAC 생성·검증으로 동작하고 대칭키는 서명이 용도 불일치다")
    void hmacAndPurpose() {
        String uid = create(KeyAlgorithm.SHA512, 512, null);
        SignResponse s = tests.sign(uid, PLAIN, "admin");
        assertTrue(tests.verify(uid, PLAIN, s.signature(), "admin").valid());
        assertFalse(tests.verify(uid, "변조", s.signature(), "admin").valid());

        String aes = create(KeyAlgorithm.AES, 256, KeyMode.GCM);
        assertEquals(ErrorCode.KEY_PURPOSE_MISMATCH, fail(() -> tests.sign(aes, PLAIN, "admin")));
    }
}
