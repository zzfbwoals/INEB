package com.ineb.kms.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ineb.kms.crypto.ConfigSecretCodec;
import com.ineb.kms.crypto.CryptoConstants;
import com.ineb.kms.crypto.MasterKeyException;
import com.ineb.kms.domain.AdminUser;
import com.ineb.kms.repository.AdminUserRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminUserSeederTest {

    private static final String PASSPHRASE = "correct-master-passphrase-over-20";
    private static final String INIT_PASSWORD = "Admin!@#$5";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private MockEnvironment env(String passphrase, String initPasswordProperty) {
        MockEnvironment env = new MockEnvironment();
        if (passphrase != null) {
            env.setProperty(CryptoConstants.MASTER_PASSPHRASE_ENV, passphrase);
        }
        if (initPasswordProperty != null) {
            env.setProperty(AdminUserSeeder.INIT_PASSWORD_PROPERTY, initPasswordProperty);
        }
        return env;
    }

    private String encrypted() {
        return ConfigSecretCodec.encrypt(PASSPHRASE.toCharArray(), INIT_PASSWORD.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("admin_user가 비어 있으면 ENC 초기 비밀번호를 복호화해 BCrypt 해시로 시드한다")
    void seedsAdminWithDecryptedPassword() {
        AdminUserRepository repo = mock(AdminUserRepository.class);
        when(repo.count()).thenReturn(0L);

        new AdminUserSeeder(repo, passwordEncoder, env(PASSPHRASE, encrypted())).run(null);

        ArgumentCaptor<AdminUser> captor = ArgumentCaptor.forClass(AdminUser.class);
        verify(repo).save(captor.capture());
        assertEquals("admin", captor.getValue().getLoginId());
        assertTrue(passwordEncoder.matches(INIT_PASSWORD, captor.getValue().getPasswordHash()));
    }

    @Test
    @DisplayName("admin_user가 이미 있으면 설정값을 읽지 않고 아무것도 하지 않는다")
    void skipsWhenAdminExists() {
        AdminUserRepository repo = mock(AdminUserRepository.class);
        when(repo.count()).thenReturn(1L);

        new AdminUserSeeder(repo, passwordEncoder, env(null, null)).run(null);

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("초기 비밀번호가 ENC 형식이 아닌 평문이면 기동을 중단한다")
    void rejectsPlainInitPassword() {
        AdminUserRepository repo = mock(AdminUserRepository.class);
        when(repo.count()).thenReturn(0L);
        AdminUserSeeder seeder = new AdminUserSeeder(repo, passwordEncoder, env(PASSPHRASE, INIT_PASSWORD));

        assertThrows(MasterKeyException.class, () -> seeder.run(null));
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("패스프레이즈가 틀리면 복호화 실패로 기동을 중단한다")
    void rejectsWrongPassphrase() {
        AdminUserRepository repo = mock(AdminUserRepository.class);
        when(repo.count()).thenReturn(0L);
        AdminUserSeeder seeder = new AdminUserSeeder(repo, passwordEncoder, env("wrong-passphrase", encrypted()));

        assertThrows(MasterKeyException.class, () -> seeder.run(null));
        verify(repo, never()).save(any());
    }
}
