package com.ineb.kms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 공지사항. 첨부파일(NoticeFile)은 단방향 N:1 로만 연결한다 — 이쪽에 컬렉션을 두면 목록 조회가 암호문(enc_data)까지 읽는다.
 * pinned(중요) 는 목록 상단 고정·배지 표시용이며 설계 초안의 expose_yn(노출여부)을 대체한다 (2026-09-08 확정).
 * 조회수는 상세 조회 시 JPQL update 로 증가시키므로 updated_at 이 바뀌지 않는다.
 */
@Entity
@Table(name = "notice")
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private boolean pinned;

    /** 작성자 로그인 ID — 감사 로그 actor 와 같은 값 */
    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    /** 작성자 표시 이름(작성 시점 스냅샷) — 목록 표시·작성자 검색 대상 */
    @Column(name = "author_name", nullable = false, length = 50)
    private String authorName;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Notice() {
    }

    public Notice(String title, String content, boolean pinned, String createdBy, String authorName) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.createdBy = createdBy;
        this.authorName = authorName;
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

    public void edit(String title, String content, boolean pinned) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public boolean isPinned() {
        return pinned;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getAuthorName() {
        return authorName;
    }

    public long getViewCount() {
        return viewCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
