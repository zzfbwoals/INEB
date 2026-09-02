package com.ineb.kms.user.dto;

import com.ineb.kms.domain.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 사용자 수정 — 목업 수정 모달과 동일하게 상태를 포함하며, password 는 재설정할 때만 보낸다. */
public record UserUpdateRequest(
        @NotBlank(message = "이름을 입력해주세요.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,
        @NotBlank(message = "연락처를 입력해주세요.")
        @Pattern(regexp = "^01[016789]-\\d{3,4}-\\d{4}$", message = "연락처는 010-0000-0000 형식이어야 합니다.")
        String phone,
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
        String email,
        @NotNull(message = "상태를 선택해주세요.")
        UserStatus status,
        String password) {
}
