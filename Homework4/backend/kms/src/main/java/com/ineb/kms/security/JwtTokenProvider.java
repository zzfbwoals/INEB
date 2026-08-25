package com.ineb.kms.security;

import com.ineb.kms.crypto.WrappedSecretStore;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * HS256 JWT 발급/검증.
 * 서명 키는 환경변수가 아니라 WrappedSecretStore(crypto_config에 마스터키로 래핑 보관)에서 가져온다.
 * 최초 기동 시 SecureRandom 32바이트로 자동 생성되므로 서버마다 다른 값이 되고, 운영자가 관리할 비밀이 늘지 않는다.
 */
@Component
public class JwtTokenProvider {

    private static final long DEFAULT_VALIDITY_MILLIS = 60L * 60 * 1000; // 60분

    private final SecretKey key;
    private final long validityMillis;

    @Autowired
    public JwtTokenProvider(WrappedSecretStore secretStore) {
        this(secretStore.jwtKey(), DEFAULT_VALIDITY_MILLIS);
    }

    // 테스트용: 서명 키 바이트와 유효기간을 직접 지정
    public JwtTokenProvider(byte[] secret, long validityMillis) {
        this.key = Keys.hmacShaKeyFor(secret);
        this.validityMillis = validityMillis;
    }

    // 테스트용: 문자열 비밀키 (32바이트 이상)
    public JwtTokenProvider(String secret, long validityMillis) {
        this(secret.getBytes(StandardCharsets.UTF_8), validityMillis);
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
