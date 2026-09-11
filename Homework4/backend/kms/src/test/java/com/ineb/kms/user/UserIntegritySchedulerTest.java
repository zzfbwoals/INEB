package com.ineb.kms.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ineb.kms.audit.AuditHook;
import com.ineb.kms.domain.AppUser;
import com.ineb.kms.domain.UserStatus;
import com.ineb.kms.integrity.IntegrityFlagService;
import com.ineb.kms.integrity.IntegrityFlagTestSupport;
import com.ineb.kms.repository.AppUserRepository;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 배치는 트리거 알림의 안전망 — 위반 표시가 없는 불일치만 기록하고, 원복돼도 자동 복구 기록은 남기지 않는다 */
class UserIntegritySchedulerTest {

    private final UserIntegrityHasher hasher = new UserIntegrityHasher(new byte[32]);
    private final List<AppUser> users = new ArrayList<>();
    private final List<String> audits = new ArrayList<>();
    private IntegrityFlagService flags;
    private UserIntegrityScheduler scheduler;

    @BeforeEach
    void setUp() {
        AppUserRepository repository = mock(AppUserRepository.class);
        when(repository.findAll()).thenAnswer(inv -> new ArrayList<>(users));
        IntegrityFlagTestSupport.Fixture fx = IntegrityFlagTestSupport.create(
                (actor, action, target, detail) -> audits.add(actor + ":" + action + ":" + target));
        flags = fx.flags();
        scheduler = new UserIntegrityScheduler(repository, hasher, flags, true);
    }

    private AppUser user(long id, String name) {
        AppUser u = new AppUser(name, "$2a$10$hash", UserStatus.ACTIVE, "p", "e", "eh");
        try {
            Field f = AppUser.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        hasher.rehash(u);
        users.add(u);
        return u;
    }

    @Test
    @DisplayName("정상 사용자만 있으면 아무 기록도 남기지 않는다")
    void noViolation() {
        user(1, "홍길동");
        user(2, "김철수");
        assertEquals(0, scheduler.sweep());
        assertEquals(0, audits.size());
    }

    @Test
    @DisplayName("변조된 사용자는 첫 주기에만 USER_INTEGRITY_VIOLATION 을 남기고, 지속 위반은 재기록하지 않는다")
    void violationRecordedOnce() {
        AppUser a = user(1, "홍길동");
        user(2, "김철수");
        a.changeStatus(UserStatus.SUSPENDED);           // 해시 재계산 없이 변조

        assertEquals(1, scheduler.sweep());
        assertEquals(List.of("SYSTEM:USER_INTEGRITY_VIOLATION:USER#1"), audits);

        assertEquals(0, scheduler.sweep());
        assertEquals(0, scheduler.sweep());
        assertEquals(1, audits.size());
    }

    @Test
    @DisplayName("트리거 알림이 이미 위반을 기록한 사용자는 배치가 다시 기록하지 않는다")
    void alreadyFlaggedByTriggerSkipped() {
        AppUser a = user(1, "홍길동");
        a.changeStatus(UserStatus.SUSPENDED);
        flags.flag(AuditHook.userTarget(1L), "trigger");   // 변경 알림 핸들러가 먼저 기록
        assertEquals(0, scheduler.sweep());
        assertEquals(1, audits.size());
    }

    @Test
    @DisplayName("DB 직접 원복으로 해시가 다시 일치해도 자동 복구 기록 없이 위반 표시가 유지된다")
    void restoreDoesNotClear() {
        AppUser a = user(1, "홍길동");
        a.changeStatus(UserStatus.SUSPENDED);
        scheduler.sweep();

        a.changeStatus(UserStatus.ACTIVE);              // 원복 — 해시 일치
        assertTrue(hasher.verify(a));
        assertEquals(0, scheduler.sweep());
        assertEquals(1, audits.size());                 // USER_INTEGRITY_RESTORED 같은 자동 해제 기록 없음
        assertTrue(flags.isFlagged(AuditHook.userTarget(1L)));
    }

    @Test
    @DisplayName("관리자 재해시(USER_INTEGRITY_RESEALED) 뒤에만 정상이 되고, 다시 변조되면 다시 기록한다")
    void resealClearsThenNewViolationRecorded() {
        AppUser a = user(1, "홍길동");
        a.changeStatus(UserStatus.SUSPENDED);
        scheduler.sweep();

        hasher.rehash(a);                               // 재해시 API 가 하는 일: 현재 값으로 재봉인 + 기록
        flags.reseal(AuditHook.userTarget(1L), "admin", "reason=확인");
        assertFalse(flags.isFlagged(AuditHook.userTarget(1L)));
        assertEquals(0, scheduler.sweep());

        a.changeStatus(UserStatus.ACTIVE);              // 두 번째 변조
        assertEquals(1, scheduler.sweep());
        assertEquals("SYSTEM:USER_INTEGRITY_VIOLATION:USER#1", audits.getLast());
        assertEquals(3, audits.size());
    }

    @Test
    @DisplayName("enabled=false 면 tick 이 아무 일도 하지 않는다")
    void disabled() {
        AppUser a = user(1, "홍길동");
        a.changeStatus(UserStatus.SUSPENDED);
        UserIntegrityScheduler off = new UserIntegrityScheduler(mock(AppUserRepository.class), hasher, flags, false);
        off.tick();
        assertEquals(0, audits.size());
    }
}
