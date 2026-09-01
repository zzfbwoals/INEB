package com.ineb.kms.key;

import com.ineb.kms.common.ApiResponse;
import com.ineb.kms.common.PageResponse;
import com.ineb.kms.domain.KeyAction;
import com.ineb.kms.domain.KeyAlgorithm;
import com.ineb.kms.domain.KeyPurpose;
import com.ineb.kms.key.dto.HistoryItem;
import com.ineb.kms.key.dto.KeyActionRequest;
import com.ineb.kms.key.dto.KeyCreateRequest;
import com.ineb.kms.key.dto.KeyDetail;
import com.ineb.kms.key.dto.KeySummary;
import com.ineb.kms.key.dto.KeyUpdateRequest;
import com.ineb.kms.key.dto.MaterialRevealRequest;
import com.ineb.kms.key.dto.MaterialRevealResponse;
import com.ineb.kms.key.dto.UsageResponse;
import com.ineb.kms.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final KeyOperationService operationService;

    public KeyController(KeyService keyService, KeyOperationService operationService) {
        this.keyService = keyService;
        this.operationService = operationService;
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

    /** 상태는 연산의 결과로만 변한다 — body 의 action 으로 ACTIVATE/REACTIVATE/DEACTIVATE/ROTATE/DESTROY 를 지정 */
    @PatchMapping("/{keyUid}/status")
    public ApiResponse<KeyDetail> changeStatus(@PathVariable String keyUid,
                                               @Valid @RequestBody KeyActionRequest request,
                                               @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(operationService.execute(keyUid, request, principal.loginId()),
                ACTION_MESSAGES.getOrDefault(request.action(), "처리되었습니다."));
    }

    /** 버전 키값 조회 — 사유 필수, 감사로그 기록. 부수효과(감사 기록·무결성 위반 시 자동 정지)가 있어 POST. */
    @PostMapping("/{keyUid}/versions/{version}/material")
    public ApiResponse<MaterialRevealResponse> revealMaterial(@PathVariable String keyUid,
                                                              @PathVariable int version,
                                                              @Valid @RequestBody MaterialRevealRequest request,
                                                              @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(keyService.revealMaterial(keyUid, version, request.reason(), principal.loginId()),
                "키 값이 조회되었습니다.");
    }

    @GetMapping("/{keyUid}/history")
    public ApiResponse<List<HistoryItem>> history(@PathVariable String keyUid) {
        return ApiResponse.ok(keyService.history(keyUid));
    }

    @GetMapping("/{keyUid}/usage")
    public ApiResponse<UsageResponse> usage(@PathVariable String keyUid,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(keyService.usage(keyUid, page, size));
    }

    private static final Map<KeyAction, String> ACTION_MESSAGES = Map.of(
            KeyAction.ACTIVATE, "활성화되었습니다.",
            KeyAction.REACTIVATE, "재활성화되었습니다.",
            KeyAction.DEACTIVATE, "정지되었습니다.",
            KeyAction.ROTATE, "갱신되었습니다.",
            KeyAction.DESTROY, "삭제되었습니다.");
}
