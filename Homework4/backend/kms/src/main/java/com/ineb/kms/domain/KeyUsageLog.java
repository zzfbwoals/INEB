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
 * 암복호화·서명검증 테스트 호출 기록. 성공·실패(차단 포함) 모두 남기며 버전별 사용 통계의 원천이다.
 * 호출자는 기록하지 않는다 — 관리자 행위 추적은 3주차 audit_log 가 담당한다.
 */
@Entity
@Table(name = "key_usage_log")
public class KeyUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "key_id", nullable = false)
    private CryptoKey key;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UsageOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UsageResult result;

    @Column(name = "fail_reason", length = 200)
    private String failReason;

    @Column(name = "used_at", nullable = false, updatable = false)
    private Instant usedAt;

    protected KeyUsageLog() {
    }

    public KeyUsageLog(CryptoKey key, int version, UsageOperation operation, UsageResult result, String failReason) {
        this.key = key;
        this.version = version;
        this.operation = operation;
        this.result = result;
        this.failReason = failReason;
    }

    @PrePersist
    void onCreate() {
        usedAt = Instant.now();
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

    public UsageOperation getOperation() {
        return operation;
    }

    public UsageResult getResult() {
        return result;
    }

    public String getFailReason() {
        return failReason;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
