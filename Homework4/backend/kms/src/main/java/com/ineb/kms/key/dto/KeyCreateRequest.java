package com.ineb.kms.key.dto;

import com.ineb.kms.domain.KeyAlgorithm;
import com.ineb.kms.domain.KeyMode;
import com.ineb.kms.domain.KeyPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 키 등록 요청. 용도는 알고리즘이 결정하므로 생략 가능(지정 시 일치해야 함).
 * activationDate 는 KST "yyyy-MM-dd HH:mm[:ss]" — 없거나 과거면 즉시 ACTIVE, 미래면 PRE_ACTIVE.
 */
public record KeyCreateRequest(
        @NotBlank(message = "키명을 입력해주세요.")
        @Size(max = 100, message = "키명은 100자 이하여야 합니다.")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "키명은 영문·숫자·'.'·'_'·'-'만 사용할 수 있습니다.")
        String keyName,
        @NotNull(message = "알고리즘을 선택해주세요.") KeyAlgorithm algorithm,
        @NotNull(message = "키 사이즈를 선택해주세요.") Integer keySize,
        KeyMode mode,
        KeyPurpose purpose,
        Boolean autoRotate,
        Integer rotationPeriodDays,
        String activationDate,
        @Size(max = 500, message = "설명은 500자 이하여야 합니다.") String description) {

    public boolean autoRotateOrDefault() {
        return autoRotate == null || autoRotate;
    }
}
