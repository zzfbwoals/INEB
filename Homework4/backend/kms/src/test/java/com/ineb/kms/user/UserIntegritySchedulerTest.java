package com.ineb.kms.user;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ineb.kms.audit.AuditHook;
import com.ineb.kms.domain.AppUser;
import com.ineb.kms.domain.UserStatus;
import com.ineb.kms.repository.AppUserRepository;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserIntegritySchedulerTest {

    private final UserIntegrityHasher hasher = new UserIntegrityHasher(new byte[32]);
    private final List<AppUser> users = new ArrayList<>();
    private final List<String> audits = new ArrayList<>();
    private UserIntegrityScheduler scheduler;

    @BeforeEach
    void setUp() {
        AppUserRepository repository = mock(AppUserRepository.class);
        when(repository.findAll()).thenAnswer(inv -> new ArrayList<>(users));
        AuditHook audit = (actor, action, target, detail) -> audits.add(actor + ":" + action + ":" + target);
        scheduler = new UserIntegrityScheduler(repository, hasher, audit, true);
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
        assertArrayEquals(new int[] {0, 0}, scheduler.sweep());
        assertEquals(0, audits.size());
    }

    @Test
    @DisplayName("변조된 사용자는 첫 주기에만 USER_INTEGRITY_VIOLATION 을 남기고, 지속 위반은 재기록하지 않는다")
    void violationRecordedOnce() {
        AppUser a = user(1, "홍길동");
        user(2, "김철수");
        a.changeStatus(UserStatus.SUSPENDED);           // 해시 재계산 없이 변조

        assertArrayEquals(new int[] {1, 0}, scheduler.sweep());
        assertEquals(List.of("SYSTEM:USER_INTEGRITY_VIOLATION:USER#1"), audits);

        assertArrayEquals(new int[] {0, 0}, scheduler.sweep());
        assertArrayEquals(new int[] {0, 0}, scheduler.sweep());
        assertEquals(1, audits.size());
    }

    @Test
    @DisplayName("위반이 해소되면(재계산·원복) USER_INTEGRITY_RESTORED 를 한 번 남긴다")
    void restoredRecordedOnce() {
        AppUser a = user(1, "홍길동");
        a.changeStatus(UserStatus.SUSPENDED);
        scheduler.sweep();

        hasher.rehash(a);                                // PUT 수정 등으로 해시가 재계산된 상황
        assertArrayEquals(new int[] {0, 1}, scheduler.sweep());
        assertEquals("SYSTEM:USER_INTEGRITY_RESTORED:USER#1", audits.get(1));

        assertArrayEquals(new int[] {0, 0}, scheduler.sweep());
        assertEquals(2, audits.size());
    }

    @Test
    @DisplayName("삭제된(더 이상 조회되지 않는) 위반 사용자는 복구로 정리된다")
    void removedUserClearsViolation() {
        AppUser a = user(1, "홍길동");
        a.changeStatus(UserStatus.SUSPENDED);
        scheduler.sweep();

        users.clear();
        assertArrayEquals(new int[] {0, 1}, scheduler.sweep());
    }

    @Test
    @DisplayName("enabled=false 면 tick 이 아무 일도 하지 않는다")
    void disabled() {
        AppUser a = user(1, "홍길동");
        a.changeStatus(UserStatus.SUSPENDED);
        UserIntegrityScheduler off = new UserIntegrityScheduler(mock(AppUserRepository.class), hasher,
                (actor, action, target, detail) -> audits.add(action), false);
        off.tick();
        assertEquals(0, audits.size());
    }
}
