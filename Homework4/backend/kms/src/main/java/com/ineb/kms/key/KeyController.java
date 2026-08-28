package com.ineb.kms.key;

import com.ineb.kms.common.ApiResponse;
import com.ineb.kms.common.PageResponse;
import com.ineb.kms.domain.KeyAlgorithm;
import com.ineb.kms.domain.KeyPurpose;
import com.ineb.kms.key.dto.KeyCreateRequest;
import com.ineb.kms.key.dto.KeyDetail;
import com.ineb.kms.key.dto.KeySummary;
import com.ineb.kms.key.dto.KeyUpdateRequest;
import com.ineb.kms.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * KMS 관리 키 목록·상세·등록·메타 수정. 경로 변수는 외부 식별자 key_uid(UUID).
 * 상태 변경 연산(PATCH /status)·이력·사용 통계·테스트는 이후 단계에서 추가.
 */
@RestController
@RequestMapping("/api/keys")
public class KeyController {

    private final KeyService keyService;

    public KeyController(KeyService keyService) {
        this.keyService = keyService;
    }

    @GetMapping
    public ApiResponse<PageResponse<KeySummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) KeyAlgorithm algorithm,
            @RequestParam(required = false, defaultValue = "LIVE") String status,
            @RequestParam(required = false) KeyPurpose purpose,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return ApiResponse.ok(keyService.list(keyword, algorithm, status, purpose, page, size, sort, direction));
    }

    @PostMapping
    public ApiResponse<KeyDetail> create(@Valid @RequestBody KeyCreateRequest request,
                                         @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(keyService.create(request, principal.loginId()), "키가 등록되었습니다.");
    }

    @GetMapping("/{keyUid}")
    public ApiResponse<KeyDetail> get(@PathVariable String keyUid) {
        return ApiResponse.ok(keyService.get(keyUid));
    }

    @PutMapping("/{keyUid}")
    public ApiResponse<KeyDetail> update(@PathVariable String keyUid,
                                         @Valid @RequestBody KeyUpdateRequest request,
                                         @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(keyService.update(keyUid, request, principal.loginId()), "키 정보가 수정되었습니다.");
    }
}
