package com.ineb.kms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * 감사 로그 섀도(복사본) — audit_log 와 같은 내용을 같은 트랜잭션에서 이중 기록한다(AuditChainService).
 * 체인 검증이 "어디가 깨졌는지"를 알려주면, 섀도와의 비교(AuditShadowComparer)가 "무엇이 지워지고·끼어들고·바뀌었는지"를 보여준다.
 * <p>
 * DB 계정(dguard)이 owner 라 권한 분리가 불가능하므로 섀도는 무결성 보장이 아니라 **포렌식 증거**다 — 탐지는 체인이 담당한다.
 * UPDATE/DELETE/TRUNCATE 는 트리거(AuditLogSchemaMigration)로 막아 우발적 수정을 방지하고, 트리거 해제 흔적은 기동 시 감사 기록으로 남긴다.
 * id 는 원본 id 를 그대로 쓴다(생성 전략 없음). 삽입은 네이티브 ON CONFLICT DO NOTHING 으로만 한다(AuditLogShadowRepository).
 */
@Entity
@Table(name = "audit_log_shadow")
@Immutable
public class AuditLogShadow implements ChainRow {

    @Id
    private Long id;

    @Column(nullable = false, length = 50)
    private String actor;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(nullable = false, length = 120)
    private String target;

    @Column(nullable = false, columnDefinition = "text")
    private String detail;

    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;

    @Column(name = "row_hash", nullable = false, length = 64)
    private String rowHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLogShadow() {
    }

    /** 테스트·비교용 — 실제 저장은 리포지토리의 네이티브 INSERT 로만 한다 */
    public AuditLogShadow(Long id, String actor, String action, String target, String detail,
                          String prevHash, String rowHash, Instant createdAt) {
        this.id = id;
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
