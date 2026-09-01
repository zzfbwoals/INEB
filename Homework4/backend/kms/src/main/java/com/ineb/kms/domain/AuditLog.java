package com.ineb.kms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * 감사 로그 (append-only 해시 체인). UPDATE/DELETE 금지 — @Immutable 로 엔티티 변경을 차단하고
 * repository 에는 저장·조회만 둔다. row_hash = HMAC(prev_hash|actor|action|target|detail|created_at(KST)),
 * 최초 행의 prev_hash 는 "EMPTY". created_at 이 해시에 들어가므로 @PrePersist 가 아니라 생성 시점에 확정한다.
 */
@Entity
@Table(name = "audit_log")
@Immutable
public class AuditLog {

    /** 체인 시작점 — 최초 행의 prev_hash */
    public static final String CHAIN_ANCHOR = "EMPTY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String actor;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(nullable = false, length = 120)
    private String target;

    @Column(nullable = false, length = 500)
    private String detail;

    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;

    @Column(name = "row_hash", nullable = false, length = 64)
    private String rowHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(String actor, String action, String target, String detail,
                    String prevHash, String rowHash, Instant createdAt) {
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.detail = detail;
        this.prevHash = prevHash;
        this.rowHash = rowHash;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public String getDetail() {
        return detail;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public String getRowHash() {
        return rowHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
