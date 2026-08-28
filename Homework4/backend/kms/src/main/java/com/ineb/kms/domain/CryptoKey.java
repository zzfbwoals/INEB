package com.ineb.kms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * KMS 관리 키 — 논리 키(신원·메타·갱신 정책). 외부 연동용 키 자산이며 어드민 자체 데이터에는 사용하지 않는다.
 * 실제 키 재료는 버전 단위로 {@link KeyMaterial} 에 보관하고, status 는 버전 상태에서 파생되어 서버만 갱신한다.
 * 외부 식별자는 순번 대신 key_uid(UUID) 를 노출한다.
 */
@Entity
@Table(name = "crypto_key")
public class CryptoKey {

    public static final int MAX_VERSIONS = 100;
    public static final int ROTATION_MIN_DAYS = 1;
    public static final int ROTATION_MAX_DAYS = 730;
    public static final int ROTATION_DEFAULT_DAYS = 90;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_uid", nullable = false, unique = true, updatable = false, length = 36)
    private String keyUid;

    @Column(name = "key_name", nullable = false, unique = true, length = 100)
    private String keyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KeyAlgorithm algorithm;

    @Column(name = "key_size", nullable = false)
    private int keySize;

    /** 대칭키만 사용. 비대칭·HMAC 은 null */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private KeyMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private KeyPurpose purpose;

    /** 파생 상태 — 버전 상태 집계. {@link #recalcStatus(Collection)} 로만 갱신 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KeyState status;

    /** 암호화·서명에 쓰는 최신 버전 포인터. ACTIVE 버전이 없어도 최근 현행 번호를 유지한다 */
    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    @Column(name = "auto_rotate", nullable = false)
    private boolean autoRotate;

    @Column(name = "rotation_period_days")
    private Integer rotationPeriodDays;

    @Column(name = "next_rotation_at")
    private Instant nextRotationAt;

    @Column(length = 500)
    private String description;

    /** HMAC-SHA256(정규화 문자열) 16진수 64자. 등록·수정·모든 상태 전이 직후 재계산 */
    @Column(name = "integrity_hash", length = 64)
    private String integrityHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CryptoKey() {
    }

    public CryptoKey(String keyName, KeyAlgorithm algorithm, int keySize, KeyMode mode, KeyPurpose purpose,
                     boolean autoRotate, Integer rotationPeriodDays, String description) {
        this.keyUid = UUID.randomUUID().toString();
        this.keyName = keyName;
        this.algorithm = algorithm;
        this.keySize = keySize;
        this.mode = mode;
        this.purpose = purpose;
        this.status = KeyState.PRE_ACTIVE;
        this.currentVersion = 1;
        this.description = description;
        changeRotation(autoRotate, rotationPeriodDays, Instant.now());
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // ---- 도메인 메서드 ----

    public void rename(String keyName, String description) {
        this.keyName = keyName;
        this.description = description;
    }

    /**
     * 갱신 정책 변경. 자동 갱신이면 다음 갱신일 = 기준 시각 + 주기, 아니면 주기·다음 갱신일을 비운다.
     */
    public void changeRotation(boolean autoRotate, Integer rotationPeriodDays, Instant baseTime) {
        this.autoRotate = autoRotate;
        if (autoRotate) {
            int days = rotationPeriodDays == null ? ROTATION_DEFAULT_DAYS : rotationPeriodDays;
            this.rotationPeriodDays = days;
            this.nextRotationAt = baseTime.plusSeconds(days * 86_400L);
        } else {
            this.rotationPeriodDays = null;
            this.nextRotationAt = null;
        }
    }

    /** 갱신이 수행된 뒤 다음 갱신일을 주기만큼 미룬다 (수동 갱신은 호출하지 않음 — 스케줄 무관). */
    public void scheduleNextRotation(Instant rotatedAt) {
        if (autoRotate && rotationPeriodDays != null) {
            this.nextRotationAt = rotatedAt.plusSeconds(rotationPeriodDays * 86_400L);
        }
    }

    /** 키 전체 정지 시 자동 갱신도 중단한다. */
    public void stopAutoRotation() {
        this.autoRotate = false;
        this.nextRotationAt = null;
    }

    public void pointCurrent(int version) {
        this.currentVersion = version;
    }

    /**
     * 키 상태 파생 규칙: ACTIVE 있음→ACTIVE / ACTIVE 없고 PRE_ACTIVE 있음→PRE_ACTIVE /
     * 모두 DESTROYED→DESTROYED / 그 외(DEACTIVATED·DESTROYED 혼재)→DEACTIVATED
     */
    public void recalcStatus(Collection<KeyMaterial> materials) {
        boolean anyActive = false;
        boolean anyPreActive = false;
        boolean allDestroyed = !materials.isEmpty();
        for (KeyMaterial m : materials) {
            switch (m.getState()) {
                case ACTIVE -> anyActive = true;
                case PRE_ACTIVE -> anyPreActive = true;
                default -> { }
            }
            if (m.getState() != KeyState.DESTROYED) {
                allDestroyed = false;
            }
        }
        if (anyActive) {
            this.status = KeyState.ACTIVE;
        } else if (anyPreActive) {
            this.status = KeyState.PRE_ACTIVE;
        } else if (allDestroyed) {
            this.status = KeyState.DESTROYED;
        } else {
            this.status = KeyState.DEACTIVATED;
        }
    }

    public void applyIntegrityHash(String integrityHash) {
        this.integrityHash = integrityHash;
    }

    // ---- getter ----

    public Long getId() {
        return id;
    }

    public String getKeyUid() {
        return keyUid;
    }

    public String getKeyName() {
        return keyName;
    }

    public KeyAlgorithm getAlgorithm() {
        return algorithm;
    }

    public int getKeySize() {
        return keySize;
    }

    public KeyMode getMode() {
        return mode;
    }

    public KeyPurpose getPurpose() {
        return purpose;
    }

    public KeyState getStatus() {
        return status;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public boolean isAutoRotate() {
        return autoRotate;
    }

    public Integer getRotationPeriodDays() {
        return rotationPeriodDays;
    }

    public Instant getNextRotationAt() {
        return nextRotationAt;
    }

    public String getDescription() {
        return description;
    }

    public String getIntegrityHash() {
        return integrityHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
