package com.ineb.kms.auth;

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

    public AuthService(AdminUserRepository adminUserRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // 계정 없음·비밀번호 불일치·비활성 계정 모두 동일 응답 (계정 존재 여부 비노출)
        AdminUser user = adminUserRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        if (user.getStatus() != AdminStatus.ACTIVE
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.createToken(
                user.getLoginId(), user.getName(), user.getRole().name());
        return new LoginResponse(accessToken, user.getName(), user.getRole().name());
    }
}
