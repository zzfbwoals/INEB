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

/**
 * 앱 사용자 — 어드민의 개인정보 보호 대상 데이터.
 * <p>
 * 연락처·이메일은 마스터키 AES-256-GCM 으로 암호화해 base64(iv|ct+tag)로 저장한다 (iv 동봉,
 * 별도 iv 컬럼 없음 — 필드마다 새 랜덤 iv 를 쓰므로 같은 키에서 iv 재사용이 생기지 않는다.
 * 2026-09-01 설계 개정). 정확검색은 HMAC 해시 컬럼(phone_hash/email_hash)으로만 지원한다.
 * 행 무결성 정규화(설계서 10.3): name|password_hash|status|enc_ver — 암호문 컬럼 변조는
 * 복호화 시 GCM 태그 불일치로 잡힌다.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    /** 마스터키 세대 — 패스프레이즈 변경(전체 재암호화)은 과제 범위 외, 항상 1 */
    public static final int ENC_VER_CURRENT = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "phone_enc", nullable = false, length = 200)
    private String phoneEnc;

    @Column(name = "email_enc", nullable = false, length = 400)
    private String emailEnc;

    @Column(name = "phone_hash", nullable = false, length = 64)
    private String phoneHash;

    @Column(name = "email_hash", nullable = false, unique = true, length = 64)
    private String emailHash;

    @Column(name = "enc_ver", nullable = false)
    private int encVer;

    @Column(name = "integrity_hash", length = 64)
    private String integrityHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUser() {
    }

    public AppUser(String name, String passwordHash, UserStatus status,
                   String phoneEnc, String phoneHash, String emailEnc, String emailHash) {
        this.name = name;
        this.passwordHash = passwordHash;
        this.status = status;
        this.phoneEnc = phoneEnc;
        this.phoneHash = phoneHash;
        this.emailEnc = emailEnc;
        this.emailHash = emailHash;
        this.encVer = ENC_VER_CURRENT;
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

    public void rename(String name) {
        this.name = name;
    }

    public void changeStatus(UserStatus status) {
        this.status = status;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void applyPhone(String phoneEnc, String phoneHash) {
        this.phoneEnc = phoneEnc;
        this.phoneHash = phoneHash;
    }

    public void applyEmail(String emailEnc, String emailHash) {
        this.emailEnc = emailEnc;
        this.emailHash = emailHash;
    }

    public void applyIntegrityHash(String integrityHash) {
        this.integrityHash = integrityHash;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getPhoneEnc() {
        return phoneEnc;
    }

    public String getEmailEnc() {
        return emailEnc;
    }

    public String getPhoneHash() {
        return phoneHash;
    }

    public String getEmailHash() {
        return emailHash;
    }

    public int getEncVer() {
        return encVer;
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
