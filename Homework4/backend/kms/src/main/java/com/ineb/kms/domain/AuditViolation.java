package com.ineb.kms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 감사 로그 변조 증거 (2026-09-11). 섀도 비교가 삭제·삽입·수정 행을 처음 찾은 순간 그 행의 값을 스냅샷으로 남긴다.
 * 이후 DB 를 원래대로 되돌려 섀도와 차이가 사라져도 이 증거는 남으므로 감사 로그 화면은 해당 행을 계속 빨갛게 표시하고
 * 어느 컬럼이 바뀌었는지({@code fields})를 보여준다. 앱은 INSERT 만 한다(append-only). 같은 행이 다른 컬럼으로 다시 변조되면
 * 행이 하나 더 쌓인다(unique: audit_id, kind, fields). 탐지는 해시 체인·AUDIT_CHAIN_VIOLATION 기록이 담당하고 이 표는 "무엇이 바뀌었나"의 증거다.
 * 스냅샷 = 변조된 시점의 값(MODIFIED·INSERTED 는 변조/삽입된 원본 행, DELETED 는 지워진 행의 섀도 값).
 */
@Entity
@Table(name = "audit_violation")
public class AuditViolation {

    public static final String MODIFIED = "MODIFIED";
    public static final String INSERTED = "INSERTED";
    public static final String DELETED = "DELETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대상 audit_log.id */
    @Column(name = "audit_id", nullable = false)
    private Long auditId;

    @Column(nullable = false, length = 10)
    private String kind;

    /** 바뀐 컬럼(쉼표 구분, MODIFIED 만; 그 외 빈 문자열) */
    @Column(nullable = false, length = 200)
    private String fields;

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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    protected AuditViolation() {
    }

    public AuditViolation(String kind, String fields, ChainRow snapshot, Instant detectedAt) {
        this.auditId = snapshot.getId();
        this.kind = kind;
        this.fields = fields == null ? "" : fields;
        this.actor = snapshot.getActor();
        this.action = snapshot.getAction();
        this.target = snapshot.getTarget();
        this.detail = snapshot.getDetail();
        this.prevHash = snapshot.getPrevHash();
        this.rowHash = snapshot.getRowHash();
        this.createdAt = snapshot.getCreatedAt();
        this.detectedAt = detectedAt;
    }

    /** 변조 시점 값을 ChainRow 로 — 조회 응답 변환(toItem)에 그대로 쓴다 (AuditLogShadow 는 저장하지 않는 운반체) */
    public AuditLogShadow snapshot() {
        return new AuditLogShadow(auditId, actor, action, target, detail, prevHash, rowHash, createdAt);
    }

    public Long getId() {
        return id;
    }

    public Long getAuditId() {
        return auditId;
    }

    public String getKind() {
        return kind;
    }

    public String getFields() {
        return fields;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }
}
