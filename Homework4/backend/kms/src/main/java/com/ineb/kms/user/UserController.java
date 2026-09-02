package com.ineb.kms.user;

import com.ineb.kms.common.ApiResponse;
import com.ineb.kms.common.PageResponse;
import com.ineb.kms.domain.UserStatus;
import com.ineb.kms.security.AuthPrincipal;
import com.ineb.kms.user.dto.PlainViewRequest;
import com.ineb.kms.user.dto.UserCreateRequest;
import com.ineb.kms.user.dto.UserPlainResponse;
import com.ineb.kms.user.dto.UserSummary;
import com.ineb.kms.user.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** keyword 는 이름 부분검색, phone/email 은 HMAC 해시 정확검색 (전체 값을 입력해야 한다) */
    @GetMapping
    public ApiResponse<PageResponse<UserSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return ApiResponse.ok(userService.list(keyword, phone, email, status, page, size, sort, direction));
    }

    @PostMapping
    public ApiResponse<UserSummary> create(@Valid @RequestBody UserCreateRequest request,
                                           @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(userService.create(request, principal.loginId()), "사용자가 등록되었습니다.");
    }

    @GetMapping("/{id}")
    public ApiResponse<UserSummary> get(@PathVariable Long id) {
        return ApiResponse.ok(userService.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserSummary> update(@PathVariable Long id,
                                           @Valid @RequestBody UserUpdateRequest request,
                                           @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(userService.update(id, request, principal.loginId()), "사용자 정보가 수정되었습니다.");
    }

    /** 개인정보 원문 조회 — ADMIN 한정, 사유 필수. 부수효과(감사 기록)가 있어 POST. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/plain")
    public ApiResponse<UserPlainResponse> viewPlain(@PathVariable Long id,
                                                    @Valid @RequestBody PlainViewRequest request,
                                                    @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(userService.viewPlain(id, request.reason(), principal.loginId()),
                "원문이 조회되었습니다.");
    }
}
