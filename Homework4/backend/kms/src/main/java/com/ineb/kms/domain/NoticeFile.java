package com.ineb.kms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 공지사항 첨부파일 — 마스터키 AES-256-GCM 암호문을 base64(iv|ct+tag) 문자열로 enc_data(text)에 저장한다
 * (AppUser 개인정보와 같은 봉투, iv 동봉·파일시스템 미사용). 원본은 절대 저장하지 않으며 다운로드 시에만 복호화한다.
 * 목록·삭제 경로는 enc_data 를 읽지 않도록 NoticeFileRepository 의 프로젝션·JPQL 만 쓴다.
 */
@Entity
@Table(name = "notice_file")
public class NoticeFile {

    /** 마스터키 세대 — 패스프레이즈 변경(전체 재암호화)은 과제 범위 외, 항상 1 */
    public static final int ENC_VER_CURRENT = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notice_id", nullable = false)
    private Notice notice;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    /** 평문 크기(바이트) — 화면 표시용 */
    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "enc_data", nullable = false, columnDefinition = "text")
    private String encData;

    @Column(name = "enc_ver", nullable = false)
    private int encVer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NoticeFile() {
    }

    public NoticeFile(Notice notice, String originalName, String contentType, long fileSize, String encData) {
        this.notice = notice;
        this.originalName = originalName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.encData = encData;
        this.encVer = ENC_VER_CURRENT;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Notice getNotice() {
        return notice;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getEncData() {
        return encData;
    }

    public int getEncVer() {
        return encVer;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
