package com.ineb.kms.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ineb.kms.audit.AuditHook;
import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.crypto.MasterKeyHolder;
import com.ineb.kms.crypto.PersonalDataCodec;
import com.ineb.kms.crypto.WrappedSecretStore;
import com.ineb.kms.domain.AppUser;
import com.ineb.kms.domain.UserStatus;
import com.ineb.kms.repository.AppUserRepository;
import com.ineb.kms.user.dto.UserCreateRequest;
import com.ineb.kms.user.dto.UserPlainResponse;
import com.ineb.kms.user.dto.UserSummary;
import com.ineb.kms.user.dto.UserUpdateRequest;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

    private static final String PASSWORD = "User!2345678";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final List<String> audits = new ArrayList<>();
    private AppUserRepository repository;
    private PersonalDataCodec codec;
    private UserIntegrityHasher hasher;
    private UserService service;

    @BeforeEach
    void setUp() {
        byte[] masterKey = new byte[32];
        byte[] integrityKey = new byte[32];
        MasterKeyHolder holder = mock(MasterKeyHolder.class);
        when(holder.getKey()).thenReturn(masterKey);
        WrappedSecretStore store = mock(WrappedSecretStore.class);
        when(store.integrityKey()).thenReturn(integrityKey);
        codec = new PersonalDataCodec(holder, store);
        hasher = new UserIntegrityHasher(integrityKey);

        repository = mock(AppUserRepository.class);
        when(repository.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            setId(u, 1L);
            return u;
        });
        AuditHook audit = (actor, action, target, detail) -> audits.add(action + ":" + target + ":" + detail);
        service = new UserService(repository, codec, hasher, passwordEncoder, audit);
    }

    private static void setId(AppUser user, long id) {
        try {
            Field f = AppUser.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private UserCreateRequest createRequest() {
        return new UserCreateRequest("홍길동", "010-1234-5678", "user@ineb.co.kr", PASSWORD, null);
    }

    /** 저장돼 있던 사용자 — 서비스가 만든 것과 같은 형태로 직접 구성 */
    private AppUser existingUser() {
        AppUser u = new AppUser("홍길동", passwordEncoder.encode(PASSWORD), UserStatus.ACTIVE,
                codec.encrypt("010-1234-5678"), codec.phoneHash("010-1234-5678"),
                codec.encrypt("user@ineb.co.kr"), codec.emailHash("user@ineb.co.kr"));
        hasher.rehash(u);
        setId(u, 1L);
        return u;
    }

    @Test
    @DisplayName("등록하면 개인정보가 암호화·마스킹되고 무결성 해시와 USER_CREATED 감사가 남는다")
    void createEncryptsAndMasks() {
        UserSummary result = service.create(createRequest(), "admin");

        assertEquals("홍길동", result.name());
        assertEquals("010-****-5678", result.phoneMasked());
        assertEquals("us****@ineb.co.kr", result.emailMasked());
        assertEquals("ACTIVE", result.status());
        assertTrue(result.integrityValid());
        assertTrue(audits.getFirst().startsWith("USER_CREATED:USER#1"));
    }

    @Test
    @DisplayName("이메일이 중복되면 409 USER_EMAIL_DUPLICATE")
    void duplicateEmailRejected() {
        when(repository.existsByEmailHash(anyString())).thenReturn(true);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.create(createRequest(), "admin"));
        assertEquals(ErrorCode.USER_EMAIL_DUPLICATE, e.getErrorCode());
    }

    @Test
    @DisplayName("비밀번호 정책: 8자 미만 또는 특수문자 없음은 400")
    void passwordPolicy() {
        assertThrows(BusinessException.class, () -> UserService.validatePassword("Ab!4567"));      // 7자
        assertThrows(BusinessException.class, () -> UserService.validatePassword("Abcdefgh1"));    // 특수문자 없음
        UserService.validatePassword(PASSWORD);   // 통과
    }

    @Test
    @DisplayName("수정하면 재암호화·해시 재계산되고 변경 필드가 감사 detail 에 남는다")
    void updateReencryptsAndAudits() {
        AppUser existing = existingUser();   // when(...) 안에서 다른 mock 을 호출하지 않도록 먼저 생성
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        audits.clear();

        UserSummary result = service.update(1L,
                new UserUpdateRequest("홍길순", "010-1234-5678", "user@ineb.co.kr", UserStatus.SUSPENDED, "New!2345678"),
                "admin");

        assertEquals("홍길순", result.name());
        assertEquals("SUSPENDED", result.status());
        assertTrue(result.integrityValid());
        String audit = audits.getFirst();
        assertTrue(audit.startsWith("USER_UPDATED:USER#1"));
        assertTrue(audit.contains("name"));
        assertTrue(audit.contains("status:ACTIVE→SUSPENDED"));
        assertTrue(audit.contains("password"));
    }

    @Test
    @DisplayName("원문 조회는 복호화된 값을 반환하고 사유와 함께 USER_PLAIN_VIEWED 를 기록한다")
    void viewPlainDecryptsAndAudits() {
        AppUser existing = existingUser();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        audits.clear();

        UserPlainResponse plain = service.viewPlain(1L, "CS 본인확인", "admin");

        assertEquals("010-1234-5678", plain.phone());
        assertEquals("user@ineb.co.kr", plain.email());
        assertTrue(audits.getFirst().contains("USER_PLAIN_VIEWED:USER#1"));
        assertTrue(audits.getFirst().contains("reason=CS 본인확인"));
    }

    @Test
    @DisplayName("없는 사용자는 404 USER_NOT_FOUND")
    void notFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.viewPlain(99L, "사유", "admin"));
        assertEquals(ErrorCode.USER_NOT_FOUND, e.getErrorCode());
    }
}
