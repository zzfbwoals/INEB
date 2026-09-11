package com.ineb.kms.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ineb.kms.audit.AuditLogService;
import com.ineb.kms.integrity.IntegrityFlagTestSupport;
import com.ineb.kms.audit.dto.AuditVerifyResponse;
import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.common.KstTime;
import com.ineb.kms.crypto.WrappedSecretStore;
import com.ineb.kms.dashboard.dto.DashboardSummary;
import com.ineb.kms.dashboard.dto.ExpiringItem;
import com.ineb.kms.dashboard.dto.UsageTrend;
import com.ineb.kms.domain.AppUser;
import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.KeyAlgorithm;
import com.ineb.kms.domain.KeyMaterial;
import com.ineb.kms.domain.KeyMode;
import com.ineb.kms.domain.KeyPurpose;
import com.ineb.kms.domain.KeyState;
import com.ineb.kms.domain.KeyUsageLog;
import com.ineb.kms.domain.UsageOperation;
import com.ineb.kms.domain.UsageResult;
import com.ineb.kms.domain.UserStatus;
import com.ineb.kms.key.KeyIntegrityHasher;
import com.ineb.kms.repository.AppUserRepository;
import com.ineb.kms.repository.AuditLogRepository;
import com.ineb.kms.repository.CryptoKeyRepository;
import com.ineb.kms.repository.KeyMaterialRepository;
import com.ineb.kms.repository.KeyUsageLogRepository;
import com.ineb.kms.repository.NoticeFileRepository;
import com.ineb.kms.repository.NoticeRepository;
import com.ineb.kms.user.UserIntegrityHasher;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {

    private CryptoKeyRepository keyRepository;
    private KeyMaterialRepository materialRepository;
    private KeyUsageLogRepository usageLogRepository;
    private AppUserRepository userRepository;
    private NoticeRepository noticeRepository;
    private NoticeFileRepository fileRepository;
    private AuditLogRepository auditLogRepository;
    private AuditLogService auditLogService;
    private KeyIntegrityHasher keyHasher;
    private UserIntegrityHasher userHasher;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        keyRepository = mock(CryptoKeyRepository.class);
        materialRepository = mock(KeyMaterialRepository.class);
        usageLogRepository = mock(KeyUsageLogRepository.class);
        userRepository = mock(AppUserRepository.class);
        noticeRepository = mock(NoticeRepository.class);
        fileRepository = mock(NoticeFileRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        auditLogService = mock(AuditLogService.class);
        WrappedSecretStore store = mock(WrappedSecretStore.class);
        when(store.integrityKey()).thenReturn(new byte[32]);
        keyHasher = new KeyIntegrityHasher(store);
        userHasher = new UserIntegrityHasher(store);
        service = new DashboardService(keyRepository, materialRepository, usageLogRepository, userRepository,
                noticeRepository, fileRepository, auditLogRepository, keyHasher, userHasher, auditLogService,
                IntegrityFlagTestSupport.create((actor, action, target, detail) -> { }).flags());
        when(auditLogService.status()).thenReturn(new AuditVerifyResponse(true, true, false, 0, "", List.of(), null));
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private CryptoKey key(long id, String name, KeyAlgorithm algo) {
        CryptoKey k = new CryptoKey(name, algo, 256, KeyMode.GCM, KeyPurpose.ENC_DEC, true, 90, null);
        set(k, "id", id);
        set(k, "status", KeyState.ACTIVE);
        keyHasher.rehash(k);
        return k;
    }

    @Test
    @DisplayName("요약 — 키/사용자/공지 집계와 무결성 위반(메타·버전·사용자·체인) 수, 첫 위반 키를 돌려준다")
    void summaryAggregates() {
        CryptoKey ok = key(1, "OK-KEY", KeyAlgorithm.AES);
        CryptoKey tampered = key(2, "TAMPERED-KEY", KeyAlgorithm.RSA);
        set(tampered, "keyName", "RENAMED-IN-DB");       // 해시 계산 뒤 이름 변조 → 메타 위반
        KeyMaterial goodVer = new KeyMaterial(ok, 1, KeyState.ACTIVE, "wrapped", "iv", null, Instant.now());
        keyHasher.rehash(goodVer);
        KeyMaterial badVer = new KeyMaterial(ok, 2, KeyState.ACTIVE, "wrapped2", "iv2", null, Instant.now());
        keyHasher.rehash(badVer);
        set(badVer, "wrappedKey", "swapped");             // 버전 위반
        AppUser goodUser = new AppUser("정상", "$2a$10$h", UserStatus.ACTIVE, "p", "e", "eh");
        userHasher.rehash(goodUser);
        AppUser badUser = new AppUser("변조", "$2a$10$h", UserStatus.ACTIVE, "p", "e", "eh");
        userHasher.rehash(badUser);
        set(badUser, "name", "DB에서바뀜");

        when(keyRepository.count()).thenReturn(2L);
        when(keyRepository.countByStatus(KeyState.ACTIVE)).thenReturn(2L);
        when(keyRepository.findAll()).thenReturn(List.of(ok, tampered));
        when(keyRepository.findByStatusNot(KeyState.DESTROYED)).thenReturn(List.of(ok, tampered));
        when(materialRepository.count()).thenReturn(2L);
        when(materialRepository.countByStateAndVersionNotCurrent(KeyState.ACTIVE)).thenReturn(1L);
        when(materialRepository.countByState(KeyState.PRE_ACTIVE)).thenReturn(0L);
        when(materialRepository.countByState(KeyState.DEACTIVATED)).thenReturn(0L);
        when(materialRepository.findByStateNot(KeyState.DESTROYED)).thenReturn(List.of(goodVer, badVer));
        when(userRepository.count()).thenReturn(2L);
        when(userRepository.countByStatus(UserStatus.ACTIVE)).thenReturn(2L);
        when(userRepository.findAll()).thenReturn(List.of(goodUser, badUser));
        when(noticeRepository.count()).thenReturn(3L);
        when(noticeRepository.countByPinnedTrue()).thenReturn(1L);
        when(fileRepository.count()).thenReturn(4L);
        when(auditLogService.status()).thenReturn(new AuditVerifyResponse(false, false, true, 10, "",
                List.of(new AuditVerifyResponse.ViolationRange(3, 4, "TAMPERED")), null));
        when(auditLogRepository.findActionTargetSince(any())).thenReturn(List.of(
                new Object[]{"LOGIN_FAILED", "AUTH#admin"}, new Object[]{"LOGIN_FAILED", "AUTH#admin"},
                new Object[]{"LOGIN_FAILED", "AUTH#secops"}, new Object[]{"USER_PLAIN_VIEWED", "USER#1"},
                new Object[]{"KEY_INTEGRITY_VIOLATION", "KEY#x"}, new Object[]{"AUDIT_CHAIN_VIOLATION", "AUDIT"}));
        KeyUsageLog fail = new KeyUsageLog(ok, 1, UsageOperation.DECRYPT, UsageResult.FAIL, "GCM 태그 불일치");
        set(fail, "usedAt", Instant.now());
        when(usageLogRepository.findFirst20ByResultAndUsedAtGreaterThanEqualOrderByUsedAtDescIdDesc(eq(UsageResult.FAIL), any()))
                .thenReturn(List.of(fail));

        DashboardSummary s = service.summary();

        assertEquals(2, s.keys().total());
        assertEquals(2L, s.keys().byStatus().get("ACTIVE"));
        assertEquals(0L, s.keys().byStatus().get("DESTROYED"));
        assertEquals(List.of("PRE_ACTIVE", "ACTIVE", "DEACTIVATED", "DESTROYED"), List.copyOf(s.keys().byStatus().keySet()));
        assertEquals(1, s.keys().decryptOnly());
        assertEquals(2, s.users().total());
        assertEquals(3, s.notices().total());
        assertEquals(4, s.notices().files());
        assertEquals(1, s.integrity().keyMeta());
        assertEquals(1, s.integrity().keyVersion());
        assertEquals(1, s.integrity().user());
        assertEquals(1, s.integrity().auditChain());
        assertEquals(4, s.integrity().total());
        assertEquals("RENAMED-IN-DB", s.integrity().firstKeyName());
        // 보안 신호 — 고정 4개, 로그인 실패 sub 는 대상별 상위 건수
        assertEquals(4, s.signals().size());
        assertEquals("LOGIN_FAILED", s.signals().get(0).key());
        assertEquals(3, s.signals().get(0).value());
        assertEquals("AUTH#admin 2 · AUTH#secops 1", s.signals().get(0).sub());
        assertEquals(1, s.signals().get(1).value());
        assertEquals(0, s.signals().get(2).value());
        assertEquals(2, s.signals().get(3).value());
        assertEquals("키 1 · 사용자 0 · 감사 체인 1", s.signals().get(3).sub());
        assertEquals("bad", s.signals().get(3).level());
        // 연산 실패
        assertEquals(1, s.failures().size());
        assertEquals("DECRYPT", s.failures().get(0).operation());
        assertEquals("OK-KEY", s.failures().get(0).keyName());
        // 알고리즘 분포 — 건수 내림차순, 같으면 이름순
        assertEquals(List.of("AES", "RSA"), s.algorithms().stream().map(DashboardSummary.AlgoCount::algorithm).toList());
    }

    @Test
    @DisplayName("요약 — 위반이 없으면 firstKey 는 null, 로그인 실패 sub 는 '없음'")
    void summaryNoViolation() {
        when(keyRepository.findAll()).thenReturn(List.of());
        when(auditLogRepository.findActionTargetSince(any())).thenReturn(List.of());
        DashboardSummary s = service.summary();
        assertEquals(0, s.integrity().total());
        assertNull(s.integrity().firstKeyUid());
        assertEquals("없음", s.signals().get(0).sub());
    }

    @Test
    @DisplayName("사용 추이 — 최근 N일을 빈 날 0 으로 채우고 op 로 연산을 거른다")
    void usageTrendFillsDays() {
        CryptoKey k = key(1, "K", KeyAlgorithm.AES);
        KeyUsageLog enc = new KeyUsageLog(k, 1, UsageOperation.ENCRYPT, UsageResult.SUCCESS, null);
        set(enc, "usedAt", Instant.now());
        KeyUsageLog signFail = new KeyUsageLog(k, 1, UsageOperation.SIGN, UsageResult.FAIL, "x");
        set(signFail, "usedAt", Instant.now().minus(1, ChronoUnit.DAYS));
        when(usageLogRepository.findSince(any())).thenReturn(List.of(enc, signFail));

        UsageTrend all = service.usageTrend(7, "ALL");
        assertEquals(7, all.points().size());
        assertEquals(LocalDate.now(KstTime.ZONE).toString(), all.points().get(6).date());
        assertEquals(1, all.points().get(6).ok());
        assertEquals(1, all.points().get(5).fail());
        assertEquals(0, all.points().get(0).ok() + all.points().get(0).fail());

        UsageTrend enc7 = service.usageTrend(7, "ENC");
        assertEquals(0, enc7.points().get(5).fail());   // SIGN 실패는 제외
        assertEquals("ENC", enc7.op());

        assertEquals(30, service.usageTrend(30, null).points().size());
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(BusinessException.class, () -> service.usageTrend(14, "ALL")).getErrorCode());
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(BusinessException.class, () -> service.usageTrend(7, "HMAC")).getErrorCode());
    }

    @Test
    @DisplayName("갱신 임박·예약 활성 — 자동 갱신 키의 다음 갱신일과 PRE_ACTIVE 활성일을 D-day 와 함께 시각순으로 돌려준다")
    void expiringSortedWithDday() {
        CryptoKey rot = key(1, "ROT", KeyAlgorithm.AES);
        set(rot, "nextRotationAt", Instant.now().plus(4, ChronoUnit.DAYS));
        CryptoKey pre = key(2, "PRE", KeyAlgorithm.LEA);
        KeyMaterial scheduled = new KeyMaterial(pre, 2, KeyState.PRE_ACTIVE, "w", "i", null, Instant.now().plus(2, ChronoUnit.DAYS));
        when(keyRepository.findByAutoRotateTrueAndStatusAndNextRotationAtLessThanEqual(eq(KeyState.ACTIVE), any()))
                .thenReturn(List.of(rot));
        when(materialRepository.findByStateAndActivationDateLessThanEqual(eq(KeyState.PRE_ACTIVE), any()))
                .thenReturn(List.of(scheduled));

        List<ExpiringItem> items = service.expiring(30);

        assertEquals(2, items.size());
        assertEquals("ACTIVATION", items.get(0).kind());
        assertEquals("PRE", items.get(0).keyName());
        assertEquals(2, items.get(0).version());
        assertEquals(2, items.get(0).dday());
        assertEquals("ROTATION", items.get(1).kind());
        assertEquals(4, items.get(1).dday());
        assertTrue(items.get(0).at().compareTo(items.get(1).at()) < 0);
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(BusinessException.class, () -> service.expiring(0)).getErrorCode());
    }
}
