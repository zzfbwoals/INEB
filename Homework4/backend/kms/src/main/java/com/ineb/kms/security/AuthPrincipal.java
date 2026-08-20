package com.ineb.kms.security;

/** JWT에서 복원한 인증 주체. SecurityContext의 principal로 사용된다. */
public record AuthPrincipal(String loginId, String name, String role) {
}
