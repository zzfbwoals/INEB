package com.ineb.kms.auth;

import com.ineb.kms.auth.dto.LoginRequest;
import com.ineb.kms.auth.dto.LoginResponse;
import com.ineb.kms.auth.dto.MeResponse;
import com.ineb.kms.common.ApiResponse;
import com.ineb.kms.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(new MeResponse(principal.loginId(), principal.name(), principal.role()));
    }

    /** JWT 무상태 — 서버 측 무효화 없이 클라이언트가 토큰을 폐기한다. 감사로그 기록은 3주차에 연결. */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok(null, "로그아웃 되었습니다.");
    }
}
