package com.ineb.kms.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ineb.kms.audit.AuditChainScheduler;
import com.ineb.kms.audit.AuditHook;
import com.ineb.kms.crypto.WrappedSecretStore;
import com.ineb.kms.domain.AppUser;
import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.KeyAlgorithm;
import com.ineb.kms.domain.KeyMode;
import com.ineb.kms.domain.KeyPurpose;
import com.ineb.kms.domain.UserStatus;
import com.ineb.kms.key.KeyIntegrityGuard;
import com.ineb.kms.repository.AppUserRepository;
import com.ineb.kms.repository.CryptoKeyRepository;
import com.ineb.kms.user.UserIntegrityHasher;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 트리거 알림 묶음 → 앱 자신의 변경은 무시, 직접 수정은 DB_DIRECT_CHANGE 기록(SSE 로 이어짐) + 해시·체인 즉시 재검증 */
class IntegrityChangeHandlerTest {

    private final UserIntegrityHasher userHasher = new UserIntegrityHasher(fixedKeyStore());
    private final List<String> audits = new ArrayList<>();
    private AppUserRepository userRepository;
    private CryptoKeyRepository keyRepository;
    private KeyIntegrityGuard guard;
    private AuditChainScheduler chainScheduler;
    private IntegrityFlagService flags;
    private IntegrityChangeHandler handler;

    @BeforeEach
    void setUp() {
        userRepository = mock(AppUserRepository.class);
        keyRepository = mock(CryptoKeyRepository.class);
        guard = mock(KeyIntegrityGuard.class);
        chainScheduler = mock(AuditChainScheduler.class);
        IntegrityFlagTestSupport.Fixture fx = IntegrityFlagTestSupport.create(
                (actor, action, target, detail) -> audits.add(action + ":" + target + ":" + detail));
        flags = fx.flags();
        handler = new IntegrityChangeHandler(userRepository, keyRepository, userHasher, guard, flags, chainScheduler, fx.hook());
    }

    private static WrappedSecretStore fixedKeyStore() {
        WrappedSecretStore store = mock(WrappedSecretStore.class);
        when(store.integrityKey()).thenReturn(new byte[32]);
        return store;
    }

    private static String note(String table, String op, String id, String ref, String app) {
        return "{\"table\":\"" + table + "\",\"op\":\"" + op + "\",\"id\":\"" + id + "\",\"ref\":\"" + ref
                + "\",\"app\":\"" + app + "\",\"user\":\"dguard\"}";
    }

    private AppUser user(long id) throws Exception {
        AppUser u = new AppUser("홍길동", "$2a$10$hash", UserStatus.ACTIVE, "p", "e", "eh");
        Field f = AppUser.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(u, id);
        userHasher.rehash(u);
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    @DisplayName("앱 자신의 연결(kms-backend)이 만든 알림은 무시하고, 다른 세션의 변경만 DB_DIRECT_CHANGE 로 기록한다")
    void appWritesIgnoredDirectRecorded() throws Exception {
        user(1);
        handler.handleBatch(List.of(note("app_user", "UPDATE", "1", "", IntegrityChangeTrigger.APP_NAME)));
        assertTrue(audits.isEmpty());

        handler.handleBatch(List.of(note("app_user", "UPDATE", "1", "", "psql")));
        assertEquals(1, audits.size());
        assertTrue(audits.getFirst().startsWith("DB_DIRECT_CHANGE:USER#1:table=app_user, op=UPDATE, rows=1, ids=1, dbUser=dguard, app=psql"));
    }

    @Test
    @DisplayName("해시 대상 변조 → DB_DIRECT_CHANGE + USER_INTEGRITY_VIOLATION 한 번, 원복 알림이 와도 표시는 남는다")
    void userTamperThenRestore() throws Exception {
        AppUser u = user(1);
        u.changeStatus(UserStatus.SUSPENDED);                      // DB 직접 변조
        handler.handleBatch(List.of(note("app_user", "UPDATE", "1", "", "psql")));
        assertEquals(2, audits.size());
        assertTrue(audits.get(1).startsWith("USER_INTEGRITY_VIOLATION:USER#1"));

        u.changeStatus(UserStatus.ACTIVE);                         // DB 직접 원복 — 해시는 다시 일치
        handler.handleBatch(List.of(note("app_user", "UPDATE", "1", "", "psql")));
        assertEquals(3, audits.size());                            // 직접 수정 흔적만 추가, 위반 해제 없음
        assertTrue(audits.get(2).startsWith("DB_DIRECT_CHANGE:USER#1"));
        assertTrue(flags.isFlagged(AuditHook.userTarget(1L)));
    }

    @Test
    @DisplayName("같은 묶음의 대량 변경은 (테이블, 연산, 대상)별 한 건으로 합치고 id 를 나열한다")
    void batchCollapsed() {
        List<String> batch = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            batch.add(note("notice", "UPDATE", String.valueOf(i), "", "psql"));
        }
        batch.add(note("notice", "DELETE", "99", "", "psql"));
        handler.handleBatch(batch);
        // notice 는 대상이 id 별이라 UPDATE 25건 = 25 대상, DELETE 1건
        assertEquals(26, audits.size());

        audits.clear();
        List<String> logs = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            logs.add(note("key_usage_log", "DELETE", String.valueOf(i), "7", "psql"));   // 키 7 을 못 찾으면 KEY#*
        }
        handler.handleBatch(logs);
        assertEquals(1, audits.size());
        assertTrue(audits.getFirst().startsWith("DB_DIRECT_CHANGE:KEY#*:table=key_usage_log, op=DELETE, rows=25, ids=1,2,3,"));
        assertTrue(audits.getFirst().contains("(+5)"));
    }

    @Test
    @DisplayName("crypto_key·key_material 알림은 키 가드로, audit_log·복사본 알림은 체인 즉시 재검증으로 넘긴다")
    void keyAndAuditRouting() {
        CryptoKey key = new CryptoKey("K", KeyAlgorithm.AES, 256, KeyMode.GCM, KeyPurpose.ENC_DEC, true, 90, null);
        when(keyRepository.findById(5L)).thenReturn(Optional.of(key));
        handler.handleBatch(List.of(
                note("crypto_key", "UPDATE", "5", key.getKeyUid(), "psql"),
                note("key_material", "UPDATE", "12", "5", "psql"),
                note("audit_log", "DELETE", "40", "", "psql"),
                note("audit_log_shadow", "INSERT", "41", "", "psql")));
        verify(guard, times(1)).enforceOnRead(key);            // 같은 키는 한 번만
        verify(chainScheduler, times(1)).checkNow();           // 체인도 한 번만
        assertTrue(audits.stream().anyMatch(a -> a.startsWith("DB_DIRECT_CHANGE:KEY#" + key.getKeyUid() + ":table=crypto_key")));
        assertTrue(audits.stream().anyMatch(a -> a.startsWith("DB_DIRECT_CHANGE:AUDIT:table=audit_log, op=DELETE")));
    }

    @Test
    @DisplayName("대상 규칙 — 첨부는 소속 공지, admin_user 는 AUTH#, crypto_config 는 CONFIG#, 형식 오류는 무시")
    void targets() {
        handler.handleBatch(List.of(
                note("notice_file", "DELETE", "3", "8", "psql"),
                note("admin_user", "UPDATE", "1", "admin", "psql"),
                note("crypto_config", "UPDATE", "kcv", "", "psql"),
                "garbage"));
        assertEquals(3, audits.size());
        assertTrue(audits.get(0).startsWith("DB_DIRECT_CHANGE:NOTICE#8:"));
        assertTrue(audits.get(1).startsWith("DB_DIRECT_CHANGE:AUTH#admin:"));
        assertTrue(audits.get(2).startsWith("DB_DIRECT_CHANGE:CONFIG#kcv:"));
        verify(guard, never()).enforceOnRead(any());
    }

    @Test
    @DisplayName("application_name 미적용 환경에서는 audit_log INSERT 알림을 버려 자기 기록 루프를 막는다")
    void ownNameNotAppliedGuardsLoop() {
        handler.setOwnNameApplied(false);
        handler.handleBatch(List.of(note("audit_log", "INSERT", "50", "", "")));
        assertTrue(audits.isEmpty());
        verify(chainScheduler, never()).checkNow();
    }
}
