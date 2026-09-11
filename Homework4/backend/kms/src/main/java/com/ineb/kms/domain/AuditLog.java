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
 * <p>
 * detail 암호화(2026-09-09 설계 변경): 상세는 마스터키 AES-256-GCM 으로 암호화한 base64(iv|ct+tag) 를 detail(text) 컬럼에
 * 그대로 저장한다. 해시 정규화의 detail 자리에는 저장된 값(암호문)이 들어가므로 검증에 마스터키가 필요 없다.
 * 암호화 이전에 쌓인 평문 행은 append-only 라 재암호화하지 않으며, 조회 시 복호화에 실패하는 값은 평문으로 취급한다.
 * 기존 DB 의 varchar(500) 은 기동 시 AuditLogSchemaMigration 이 text 로 바꾼다.
 */
@Entity
@Table(name = "audit_log")
@Immutable
public class AuditLog implements ChainRow {

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

    /** 상세 — 마스터키 암호문 base64(iv|ct+tag). 암호화 도입 이전 행은 평문 */
    @Column(nullable = false, columnDefinition = "text")
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

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public String getActor() {
        return actor;
    }

    @Override
    public String getAction() {
        return action;
    }

    @Override
    public String getTarget() {
        return target;
    }

    @Override
    public String getDetail() {
        return detail;
    }

    @Override
    public String getPrevHash() {
        return prevHash;
    }

    @Override
    public String getRowHash() {
        return rowHash;
    }

    @Override
    public Instant getCreatedAt() {
        return createdAt;
    }
}
