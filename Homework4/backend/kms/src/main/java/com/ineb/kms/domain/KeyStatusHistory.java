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
import java.time.Instant;

/**
 * 키 버전 상태 전이 이력 (append-only). 사유는 필수이며 관리자 연산과 서버 자동 전이(SYSTEM)를 trigger 로 구분한다.
 * from_state 가 null 이면 버전 생성(등록·갱신) 이력이다.
 */
@Entity
@Table(name = "key_status_history")
public class KeyStatusHistory {

    public static final String SYSTEM_ACTOR = "SYSTEM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "key_id", nullable = false)
    private CryptoKey key;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 20)
    private KeyState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, length = 20)
    private KeyState toState;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HistoryTrigger trigger;

    @Column(name = "changed_by", nullable = false, length = 50)
    private String changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected KeyStatusHistory() {
    }

    public KeyStatusHistory(CryptoKey key, int version, KeyState fromState, KeyState toState,
                            String reason, HistoryTrigger trigger, String changedBy) {
        this.key = key;
        this.version = version;
        this.fromState = fromState;
        this.toState = toState;
        this.reason = reason;
        this.trigger = trigger;
        this.changedBy = changedBy;
    }

    @PrePersist
    void onCreate() {
        changedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public CryptoKey getKey() {
        return key;
    }

    public int getVersion() {
        return version;
    }

    public KeyState getFromState() {
        return fromState;
    }

    public KeyState getToState() {
        return toState;
    }

    public String getReason() {
        return reason;
    }

    public HistoryTrigger getTrigger() {
        return trigger;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
