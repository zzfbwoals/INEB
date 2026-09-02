package com.ineb.kms.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ineb.kms.audit.AuditChainService;
import com.ineb.kms.auth.dto.LoginRequest;
import com.ineb.kms.auth.dto.LoginResponse;
import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.domain.AdminStatus;
import com.ineb.kms.domain.AdminUser;
import com.ineb.kms.domain.Role;
import com.ineb.kms.repository.AdminUserRepository;
import com.ineb.kms.security.JwtTokenProvider;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final String SECRET = "test-jwt-secret-key-of-at-least-32-bytes!!";
    private static final String RAW_PASSWORD = "Admin!@#$5";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AdminUserRepository adminUserRepository;
    private AuditChainService auditChain;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        adminUserRepository = mock(AdminUserRepository.class);
        auditChain = mock(AuditChainService.class);
        authService = new AuthService(
                adminUserRepository,
                passwordEncoder,
                new JwtTokenProvider(SECRET, 60L * 60 * 1000),
                auditChain);
    }

    private AdminUser adminUser(AdminStatus status) {
        return new AdminUser("admin", passwordEncoder.encode(RAW_PASSWORD), "관리자", Role.ADMIN, status);
    }

    @Test
    @DisplayName("올바른 아이디와 비밀번호로 로그인하면 토큰과 정보가 반환되고 LOGIN_SUCCESS 가 기록된다")
    void loginSuccess() {
        when(adminUserRepository.findByLoginId("admin"))
                .thenReturn(Optional.of(adminUser(AdminStatus.ACTIVE)));

        LoginResponse response = authService.login(new LoginRequest("admin", RAW_PASSWORD));

        assertNotNull(response.accessToken());
        assertEquals("관리자", response.name());
        assertEquals("ADMIN", response.role());
        verify(auditChain).append(eq("admin"), eq("LOGIN_SUCCESS"), eq("AUTH#admin"), anyString());
    }

    @Test
    @DisplayName("비밀번호가 틀리면 AUTH_INVALID_CREDENTIALS 예외와 함께 LOGIN_FAILED 가 별도 트랜잭션으로 기록된다")
    void loginFailsWithWrongPassword() {
        when(adminUserRepository.findByLoginId("admin"))
                .thenReturn(Optional.of(adminUser(AdminStatus.ACTIVE)));

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("admin", "wrong-password")));
        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, e.getErrorCode());
        verify(auditChain).appendDetached(eq("admin"), eq("LOGIN_FAILED"), eq("AUTH#admin"), anyString());
        verify(auditChain, never()).append(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("존재하지 않는 계정도 동일한 예외가 발생한다 (계정 존재 여부 비노출)")
    void loginFailsWithUnknownAccount() {
        when(adminUserRepository.findByLoginId("ghost")).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("ghost", RAW_PASSWORD)));
        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, e.getErrorCode());
        verify(auditChain).appendDetached(eq("ghost"), eq("LOGIN_FAILED"), eq("AUTH#ghost"), anyString());
    }

    @Test
    @DisplayName("잠긴 계정은 비밀번호가 맞아도 로그인할 수 없다")
    void loginFailsWithLockedAccount() {
        when(adminUserRepository.findByLoginId("admin"))
                .thenReturn(Optional.of(adminUser(AdminStatus.LOCKED)));

        BusinessException e = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("admin", RAW_PASSWORD)));
        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, e.getErrorCode());
    }

    @Test
    @DisplayName("로그아웃은 LOGOUT 감사 기록을 남긴다")
    void logoutRecordsAudit() {
        authService.logout("admin");
        verify(auditChain).append("admin", "LOGOUT", "AUTH#admin", "");
    }
}
