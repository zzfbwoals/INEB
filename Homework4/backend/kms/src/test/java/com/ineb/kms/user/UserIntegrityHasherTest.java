package com.ineb.kms.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ineb.kms.domain.AppUser;
import com.ineb.kms.domain.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserIntegrityHasherTest {

    private final UserIntegrityHasher hasher = new UserIntegrityHasher(new byte[32]);

    private AppUser user() {
        return new AppUser("홍길동", "$2a$10$hash", UserStatus.ACTIVE,
                "phoneEnc", "phoneHash", "emailEnc", "emailHash");
    }

    @Test
    @DisplayName("정규화 규칙: name|password_hash|status|enc_ver — 암호문 컬럼은 포함하지 않는다")
    void normalization() {
        assertEquals("홍길동|$2a$10$hash|ACTIVE|1", UserIntegrityHasher.normalize(user()));
    }

    @Test
    @DisplayName("해시 저장 후 검증을 통과하고, 상태를 바꾸면 재계산 전까지 위반으로 판정된다")
    void verifyDetectsChange() {
        AppUser u = user();
        assertTrue(hasher.verify(u));   // 해시 없는 행은 위반 아님
        hasher.rehash(u);
        assertTrue(hasher.verify(u));

        u.changeStatus(UserStatus.SUSPENDED);
        assertFalse(hasher.verify(u));
        hasher.rehash(u);
        assertTrue(hasher.verify(u));
    }

    @Test
    @DisplayName("암호문 컬럼 변경은 행 해시 위반이 아니다 (GCM 태그가 잡는 영역)")
    void encColumnsNotCovered() {
        AppUser u = user();
        hasher.rehash(u);
        u.applyPhone("다른암호문", "다른해시");
        assertTrue(hasher.verify(u));
    }
}
