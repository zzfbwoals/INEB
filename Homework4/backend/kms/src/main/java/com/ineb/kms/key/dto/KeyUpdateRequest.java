package com.ineb.kms.key.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 키 메타 수정 요청. 알고리즘·사이즈·모드·용도는 재료와 결합된 속성이라 수정 불가.
 * activationDate 는 현행 버전이 PRE_ACTIVE 일 때만 허용(과거로 바꾸면 즉시 활성).
 */
public record KeyUpdateRequest(
        @NotBlank(message = "키명을 입력해주세요.")
        @Size(max = 100, message = "키명은 100자 이하여야 합니다.")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "키명은 영문·숫자·'.'·'_'·'-'만 사용할 수 있습니다.")
        String keyName,
        @Size(max = 500, message = "설명은 500자 이하여야 합니다.") String description,
        @NotNull(message = "자동 갱신 여부를 지정해주세요.") Boolean autoRotate,
        Integer rotationPeriodDays,
        String activationDate) {
}
