package com.ineb.kms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 키 버전 — 상태의 주체이자 실제 키 재료 보관 행. 버전마다 서로 다른 난수(또는 키쌍)이며 키와 1:N.
 * 재료는 마스터키 AES-256-GCM 으로 래핑해 Base64 로 저장하고(wrapped_key + iv, WrappedSecretStore 와 같은 봉투 패턴),
 * 평문 재료는 API 로 절대 반환하지 않는다. DESTROYED 시 wrapped_key 를 NULL 로 물리 삭제하고 행은 감사 목적으로 남긴다.
 * 비대칭키는 개인키(PKCS#8)만 래핑하고 공개키(X.509)는 평문 Base64 로 둔다.
 */
@Entity
@Table(name = "key_material",
        uniqueConstraints = @UniqueConstraint(name = "uk_key_material_key_version", columnNames = {"key_id", "version"}))
public class KeyMaterial {

    public static final String WRAP_ALGO = "AES-256-GCM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "key_id", nullable = false)
    private CryptoKey key;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KeyState state;

    /** DEACTIVATED 진입 원인. INTEGRITY 만 REACTIVATE 허용 */
    @Enumerated(EnumType.STRING)
    @Column(name = "deactivation_trigger", length = 20)
    private DeactivationTrigger deactivationTrigger;

    /** base64(마스터키 GCM 암호문+태그). DESTROYED 면 null */
    @Column(name = "wrapped_key", columnDefinition = "text")
    private String wrappedKey;

    /** 래핑용 IV(12바이트) base64 — 테스트 암호문에 내장되는 IV 와는 별개 */
    @Column(nullable = false, length = 24)
    private String iv;

    @Column(name = "wrap_algo", nullable = false, length = 20)
    private String wrapAlgo;

    /** 비대칭키 공개키 X.509 base64. 대칭·HMAC 은 null */
    @Column(name = "public_key", columnDefinition = "text")
    private String publicKey;

    @Column(name = "activation_date", nullable = false)
    private Instant activationDate;

    @Column(name = "destroyed_at")
    private Instant destroyedAt;

    /** HMAC-SHA256(key_id|version|state|wrapped_key|iv|wrap_algo|activation_date) — 상태 전이마다 재계산 */
    @Column(name = "integrity_hash", length = 64)
    private String integrityHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected KeyMaterial() {
    }

    public KeyMaterial(CryptoKey key, int version, KeyState state, String wrappedKey, String iv,
                       String publicKey, Instant activationDate) {
        this.key = key;
        this.version = version;
        this.state = state;
        this.wrappedKey = wrappedKey;
        this.iv = iv;
        this.wrapAlgo = WRAP_ALGO;
        this.publicKey = publicKey;
        this.activationDate = activationDate;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    // ---- 도메인 메서드 (상태 변경은 KeyStateMachine 을 통해서만 호출) ----

    /**
     * 상태 전이 반영. 허용 여부 검증은 KeyStateMachine 책임이며 여기서는 부수 필드만 정리한다.
     */
    public void transition(KeyState to, DeactivationTrigger deactivationTrigger) {
        this.state = to;
        if (to == KeyState.DEACTIVATED) {
            this.deactivationTrigger = deactivationTrigger;
        } else if (to == KeyState.ACTIVE) {
            this.deactivationTrigger = null;
        }
    }

    /** 재료 물리 파기. 상태는 DESTROYED 로 함께 전이되어야 한다. */
    public void destroyMaterial(Instant destroyedAt) {
        this.wrappedKey = null;
        this.destroyedAt = destroyedAt;
    }

    /** PRE_ACTIVE 버전의 활성일 수정 또는 ACTIVATE 시 현재 시각 반영 */
    public void rescheduleActivation(Instant activationDate) {
        this.activationDate = activationDate;
    }

    public void applyIntegrityHash(String integrityHash) {
        this.integrityHash = integrityHash;
    }

    public boolean isReactivatable() {
        return state == KeyState.DEACTIVATED && deactivationTrigger == DeactivationTrigger.INTEGRITY;
    }

    public boolean isMaterialAvailable() {
        return wrappedKey != null && state != KeyState.DESTROYED;
    }

    // ---- getter ----

    public Long getId() {
        return id;
    }

    public CryptoKey getKey() {
        return key;
    }

    public int getVersion() {
        return version;
    }

    public KeyState getState() {
        return state;
    }

    public DeactivationTrigger getDeactivationTrigger() {
        return deactivationTrigger;
    }

    public String getWrappedKey() {
        return wrappedKey;
    }

    public String getIv() {
        return iv;
    }

    public String getWrapAlgo() {
        return wrapAlgo;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public Instant getActivationDate() {
        return activationDate;
    }

    public Instant getDestroyedAt() {
        return destroyedAt;
    }

    public String getIntegrityHash() {
        return integrityHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
