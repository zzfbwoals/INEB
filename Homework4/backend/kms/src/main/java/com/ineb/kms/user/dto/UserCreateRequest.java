package com.ineb.kms.user.dto;

import com.ineb.kms.domain.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 사용자 등록. 비밀번호 정책(8자 이상 + 특수문자)은 서비스에서 검증한다(재설정과 공용). */
public record UserCreateRequest(
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
        @NotBlank(message = "초기 비밀번호를 입력해주세요.")
        String password,
        UserStatus status) {

    public UserStatus statusOrDefault() {
        return status == null ? UserStatus.ACTIVE : status;
    }
}
