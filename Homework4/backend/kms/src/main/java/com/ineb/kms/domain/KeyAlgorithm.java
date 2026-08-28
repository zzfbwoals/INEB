package com.ineb.kms.domain;

import java.util.Set;

/**
 * 지원 알고리즘 카탈로그. 허용 사이즈·모드·기본 용도 규칙을 enum 이 보유해 등록 검증에 사용한다.
 * 대칭 4종은 D'GuardKMS 등록 화면(SEED/ARIA/AES/LEA), RSA·ECDSA 는 네이버 KMS 를 참고했다.
 */
public enum KeyAlgorithm {

    AES(Kind.SYMMETRIC, Set.of(128, 192, 256), Set.of(KeyMode.CBC, KeyMode.GCM, KeyMode.CTR, KeyMode.ECB)),
    ARIA(Kind.SYMMETRIC, Set.of(128, 192, 256), Set.of(KeyMode.CBC, KeyMode.GCM, KeyMode.CTR, KeyMode.ECB)),
    LEA(Kind.SYMMETRIC, Set.of(128, 192, 256), Set.of(KeyMode.CBC, KeyMode.GCM, KeyMode.CTR, KeyMode.ECB)),
    SEED(Kind.SYMMETRIC, Set.of(128), Set.of(KeyMode.CBC, KeyMode.ECB)),
    RSA(Kind.ASYMMETRIC, Set.of(2048, 3072, 4096), Set.of()),
    ECDSA(Kind.ASYMMETRIC, Set.of(256, 384), Set.of()),
    SHA256(Kind.HMAC, Set.of(256), Set.of()),
    SHA512(Kind.HMAC, Set.of(512), Set.of());

    /** 키 재료의 성격 — 재료 생성 방식과 테스트 연산 종류를 가른다. */
    public enum Kind { SYMMETRIC, ASYMMETRIC, HMAC }

    private final Kind kind;
    private final Set<Integer> sizes;
    private final Set<KeyMode> modes;

    KeyAlgorithm(Kind kind, Set<Integer> sizes, Set<KeyMode> modes) {
        this.kind = kind;
        this.sizes = sizes;
        this.modes = modes;
    }

    public Kind getKind() {
        return kind;
    }

    public Set<Integer> getSizes() {
        return sizes;
    }

    public Set<KeyMode> getModes() {
        return modes;
    }

    /** 대칭키만 운영 모드를 요구한다. */
    public boolean requiresMode() {
        return kind == Kind.SYMMETRIC;
    }

    public boolean supportsSize(int size) {
        return sizes.contains(size);
    }

    public boolean supportsMode(KeyMode mode) {
        return mode != null && modes.contains(mode);
    }

    /** 알고리즘이 결정하는 용도. 대칭→암/복호화, RSA→암/복호화 및 서명/검증, ECDSA·HMAC→서명/검증 */
    public KeyPurpose defaultPurpose() {
        return switch (this) {
            case AES, ARIA, LEA, SEED -> KeyPurpose.ENC_DEC;
            case RSA -> KeyPurpose.ENC_DEC_SIGN_VERIFY;
            case ECDSA, SHA256, SHA512 -> KeyPurpose.SIGN_VERIFY;
        };
    }
}
