package com.ineb.kms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * HS256 JWT 발급/검증.
 * 서명 키는 환경변수 JWT_SECRET(32바이트 이상)으로 주입 — 미설정 시 기동 실패(fail-fast, 마스터키와 동일 원칙).
 */
@Component
public class JwtTokenProvider {

    public static final String SECRET_ENV = "JWT_SECRET";
    private static final long DEFAULT_VALIDITY_MILLIS = 60L * 60 * 1000; // 60분

    private final SecretKey key;
    private final long validityMillis;

    @Autowired
    public JwtTokenProvider(Environment environment) {
        this(requireSecret(environment), DEFAULT_VALIDITY_MILLIS);
    }

    // 테스트용: 비밀키와 유효기간을 직접 지정
    public JwtTokenProvider(String secret, long validityMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityMillis = validityMillis;
    }

    private static String requireSecret(Environment environment) {
        String secret = environment.getProperty(SECRET_ENV);
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "환경변수 " + SECRET_ENV + "(32바이트 이상)가 설정되지 않아 기동을 중단합니다");
        }
        return secret;
    }

    public String createToken(String loginId, String name, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(loginId)
                .claim("name", name)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validityMillis))
                .signWith(key)
                .compact();
    }

    /** 서명·만료 검증 후 주체를 복원한다. 유효하지 않으면 JwtException 발생. */
    public AuthPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthPrincipal(
                claims.getSubject(),
                claims.get("name", String.class),
                claims.get("role", String.class));
    }
}
