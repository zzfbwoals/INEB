package com.ineb.kms.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "test-jwt-secret-key-of-at-least-32-bytes!!";
    private static final long ONE_HOUR = 60L * 60 * 1000;

    @Test
    @DisplayName("발급한 토큰을 파싱하면 주체 정보가 그대로 복원된다")
    void createAndParseRoundTrip() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, ONE_HOUR);

        String token = provider.createToken("admin", "관리자", "ADMIN");
        AuthPrincipal principal = provider.parse(token);

        assertEquals("admin", principal.loginId());
        assertEquals("관리자", principal.name());
        assertEquals("ADMIN", principal.role());
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 거부된다")
    void rejectsTokenSignedWithDifferentKey() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, ONE_HOUR);
        JwtTokenProvider attacker = new JwtTokenProvider("attacker-secret-key-of-at-least-32-bytes", ONE_HOUR);

        String forged = attacker.createToken("admin", "관리자", "ADMIN");

        assertThrows(JwtException.class, () -> provider.parse(forged));
    }

    @Test
    @DisplayName("변조된 토큰은 거부된다")
    void rejectsTamperedToken() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, ONE_HOUR);
        String token = provider.createToken("admin", "관리자", "ADMIN");
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThrows(JwtException.class, () -> provider.parse(tampered));
    }

    @Test
    @DisplayName("만료된 토큰은 거부된다")
    void rejectsExpiredToken() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, -1000);
        String expired = expiredProvider.createToken("admin", "관리자", "ADMIN");

        assertThrows(ExpiredJwtException.class, () -> expiredProvider.parse(expired));
    }
}
