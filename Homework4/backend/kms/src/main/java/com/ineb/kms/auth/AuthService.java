package com.ineb.kms.auth;

import com.ineb.kms.audit.AuditChainService;
import com.ineb.kms.audit.AuditHook;
import com.ineb.kms.auth.dto.LoginRequest;
import com.ineb.kms.auth.dto.LoginResponse;
import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.domain.AdminStatus;
import com.ineb.kms.domain.AdminUser;
import com.ineb.kms.repository.AdminUserRepository;
import com.ineb.kms.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditChainService auditChain;

    public AuthService(AdminUserRepository adminUserRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       AuditChainService auditChain) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditChain = auditChain;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 계정 없음·비밀번호 불일치·비활성 계정 모두 동일 응답 (계정 존재 여부 비노출 — 감사로그에도 사유를 남기지 않는다)
        AdminUser user = adminUserRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> loginFailed(request.loginId()));

        if (user.getStatus() != AdminStatus.ACTIVE
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw loginFailed(request.loginId());
        }

        String accessToken = jwtTokenProvider.createToken(
                user.getLoginId(), user.getName(), user.getRole().name());
        auditChain.append(user.getLoginId(), "LOGIN_SUCCESS",
                AuditHook.authTarget(user.getLoginId()), "role=" + user.getRole().name());
        return new LoginResponse(accessToken, user.getName(), user.getRole().name());
    }

    /** 실패 기록은 예외로 롤백되는 본 트랜잭션과 분리해 남긴다 */
    private BusinessException loginFailed(String loginId) {
        auditChain.appendDetached(loginId, "LOGIN_FAILED", AuditHook.authTarget(loginId),
                "아이디 또는 비밀번호 불일치");
        return new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Transactional
    public void logout(String loginId) {
        auditChain.append(loginId, "LOGOUT", AuditHook.authTarget(loginId), "");
    }
}
