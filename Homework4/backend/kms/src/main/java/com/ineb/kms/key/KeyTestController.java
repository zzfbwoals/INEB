package com.ineb.kms.key;

import com.ineb.kms.common.ApiResponse;
import com.ineb.kms.key.dto.CipherRequest;
import com.ineb.kms.key.dto.CipherResponse;
import com.ineb.kms.key.dto.PlainRequest;
import com.ineb.kms.key.dto.PlainResponse;
import com.ineb.kms.key.dto.SignResponse;
import com.ineb.kms.key.dto.VerifyRequest;
import com.ineb.kms.key.dto.VerifyResponse;
import com.ineb.kms.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** KMS 관리 키 동작 검증 — 키 값은 서버 내부에서만 언래핑되며 응답에 포함되지 않는다. */
@RestController
@RequestMapping("/api/keys/{keyUid}/test")
public class KeyTestController {

    private final KeyTestService testService;

    public KeyTestController(KeyTestService testService) {
        this.testService = testService;
    }

    @PostMapping("/encrypt")
    public ApiResponse<CipherResponse> encrypt(@PathVariable String keyUid, @Valid @RequestBody PlainRequest request,
                                               @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(testService.encrypt(keyUid, request.plaintext(), principal.loginId()));
    }

    @PostMapping("/decrypt")
    public ApiResponse<PlainResponse> decrypt(@PathVariable String keyUid, @Valid @RequestBody CipherRequest request,
                                              @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(testService.decrypt(keyUid, request.ciphertext(), principal.loginId()));
    }

    @PostMapping("/sign")
    public ApiResponse<SignResponse> sign(@PathVariable String keyUid, @Valid @RequestBody PlainRequest request,
                                          @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(testService.sign(keyUid, request.plaintext(), principal.loginId()));
    }

    @PostMapping("/verify")
    public ApiResponse<VerifyResponse> verify(@PathVariable String keyUid, @Valid @RequestBody VerifyRequest request,
                                              @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(testService.verify(keyUid, request.message(), request.signature(), principal.loginId()));
    }
}
