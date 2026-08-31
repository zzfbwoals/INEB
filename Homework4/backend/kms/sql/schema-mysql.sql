-- ============================================================================
-- INEB KMS 어드민 웹 — MySQL 8.x 테이블 생성 스크립트 (전체 설계 10개 테이블)
--
-- 1~6번(1~2주차 구현 완료)은 JPA 엔티티가 생성하는 스키마와 동등하게,
-- 7~10번(3~4주차 예정: app_user, notice, notice_file, audit_log)은
-- 설계 문서(류재민 설계) 기준으로 작성 — 구현 시 세부가 달라질 수 있음.
--   원본 개발 DB: PostgreSQL 17 (ddl-auto: update)
--   시각 컬럼: 애플리케이션이 UTC(Instant)로 저장하고 응답 시 KST로 변환하므로
--             타임존 없는 DATETIME(6) 사용 (값은 UTC 기준)
--   enum 컬럼: 애플리케이션(@Enumerated(STRING)) 검증에 맡기고 VARCHAR로 저장,
--             허용 값은 각 컬럼 주석 참조
--   암호문: Base64 문자열 저장 (설계 확정 — bytea/BLOB 아님)
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. crypto_config — 마스터키 유도 salt·KCV 등 key-value 설정 (비밀 아님)
--    jwt_key / integrity_key 는 마스터키로 래핑된 값이 저장됨 (WrappedSecretStore)
-- ----------------------------------------------------------------------------
CREATE TABLE crypto_config (
    config_key   VARCHAR(50)  NOT NULL COMMENT 'master_key_salt | master_key_kcv | jwt_key | integrity_key',
    config_value VARCHAR(500) NOT NULL COMMENT 'Base64 값',
    PRIMARY KEY (config_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '마스터키 유도·검증 설정 (salt·kcv, DB와 함께 백업)';

-- ----------------------------------------------------------------------------
-- 2. admin_user — 어드민 관리자 계정 (비밀번호 BCrypt, salt는 해시에 내장)
-- ----------------------------------------------------------------------------
CREATE TABLE admin_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    login_id      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(60)  NOT NULL COMMENT 'BCrypt 해시 (salt 내장, 별도 컬럼 없음)',
    name          VARCHAR(50)  NOT NULL,
    role          VARCHAR(20)  NOT NULL COMMENT 'ADMIN | OPERATOR',
    status        VARCHAR(20)  NOT NULL COMMENT 'ACTIVE | LOCKED',
    created_at    DATETIME(6)  NOT NULL COMMENT 'UTC',
    updated_at    DATETIME(6)  NOT NULL COMMENT 'UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_user_login_id (login_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '어드민 웹 관리자 계정';

-- ----------------------------------------------------------------------------
-- 3. crypto_key — KMS 관리 키(논리 키: 신원·메타·갱신 정책)
--    status 는 key_material 버전 상태에서 파생되어 서버만 갱신
-- ----------------------------------------------------------------------------
CREATE TABLE crypto_key (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    key_uid              VARCHAR(36)  NOT NULL COMMENT '외부 노출 식별자 (UUID)',
    key_name             VARCHAR(100) NOT NULL,
    algorithm            VARCHAR(20)  NOT NULL COMMENT 'AES | ARIA | LEA | SEED | RSA | ECDSA | SHA256 | SHA512',
    key_size             INT          NOT NULL COMMENT '대칭 128/192/256, RSA 2048/3072/4096, ECDSA 256/384, HMAC 256/512',
    mode                 VARCHAR(20)  NULL COMMENT '대칭키만: CBC | GCM | CTR | ECB (비대칭·HMAC은 NULL)',
    purpose              VARCHAR(30)  NOT NULL COMMENT 'ENC_DEC | ENC_DEC_SIGN_VERIFY | SIGN_VERIFY',
    status               VARCHAR(20)  NOT NULL COMMENT '파생 상태: PRE_ACTIVE | ACTIVE | DEACTIVATED | DESTROYED',
    current_version      INT          NOT NULL COMMENT '암호화·서명에 쓰는 최신 버전 포인터',
    auto_rotate          TINYINT(1)   NOT NULL,
    rotation_period_days INT          NULL COMMENT '1~730, 자동 갱신 아니면 NULL',
    next_rotation_at     DATETIME(6)  NULL COMMENT 'UTC',
    description          VARCHAR(500) NULL,
    integrity_hash       VARCHAR(64)  NULL COMMENT 'HMAC-SHA256(정규화 문자열) 16진수 64자',
    created_at           DATETIME(6)  NOT NULL COMMENT 'UTC',
    updated_at           DATETIME(6)  NOT NULL COMMENT 'UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_crypto_key_key_uid (key_uid),
    UNIQUE KEY uk_crypto_key_key_name (key_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'KMS 관리 키 (논리 키)';

-- ----------------------------------------------------------------------------
-- 4. key_material — 키 버전(상태의 주체)·래핑된 키 재료. 키와 1:N
--    DESTROYED 시 wrapped_key 를 NULL 로 물리 삭제, 행은 감사 목적으로 보존
-- ----------------------------------------------------------------------------
CREATE TABLE key_material (
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    key_id               BIGINT      NOT NULL,
    version              INT         NOT NULL,
    state                VARCHAR(20) NOT NULL COMMENT 'PRE_ACTIVE | ACTIVE | DEACTIVATED | DESTROYED',
    deactivation_trigger VARCHAR(20) NULL COMMENT 'DEACTIVATED 원인: OPERATION | INTEGRITY (INTEGRITY만 REACTIVATE 허용)',
    wrapped_key          TEXT        NULL COMMENT 'Base64(마스터키 AES-256-GCM 암호문+태그), DESTROYED면 NULL',
    iv                   VARCHAR(24) NOT NULL COMMENT '래핑용 IV(12바이트) Base64',
    wrap_algo            VARCHAR(20) NOT NULL COMMENT 'AES-256-GCM',
    public_key           TEXT        NULL COMMENT '비대칭키 공개키 X.509 Base64 (대칭·HMAC은 NULL)',
    activation_date      DATETIME(6) NOT NULL COMMENT 'UTC',
    destroyed_at         DATETIME(6) NULL COMMENT 'UTC',
    integrity_hash       VARCHAR(64) NULL COMMENT 'HMAC-SHA256(key_id|version|state|wrapped_key|iv|wrap_algo|activation_date)',
    created_at           DATETIME(6) NOT NULL COMMENT 'UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_key_material_key_version (key_id, version),
    CONSTRAINT fk_key_material_key FOREIGN KEY (key_id) REFERENCES crypto_key (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '키 버전·래핑된 키 재료';

-- ----------------------------------------------------------------------------
-- 5. key_status_history — 버전 상태 전이 이력 (append-only)
--    from_state 가 NULL 이면 버전 생성(등록·갱신) 이력
--    ※ trigger 는 MySQL 예약어라 백틱 필요
-- ----------------------------------------------------------------------------
CREATE TABLE key_status_history (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    key_id     BIGINT       NOT NULL,
    version    INT          NOT NULL,
    from_state VARCHAR(20)  NULL COMMENT 'PRE_ACTIVE | ACTIVE | DEACTIVATED | DESTROYED, 생성 이력이면 NULL',
    to_state   VARCHAR(20)  NOT NULL,
    reason     VARCHAR(500) NOT NULL,
    `trigger`  VARCHAR(20)  NOT NULL COMMENT 'OPERATION | DATE_REACHED | SCHEDULE | INTEGRITY | REACTIVATE | ROTATE',
    changed_by VARCHAR(50)  NOT NULL COMMENT '관리자 login_id 또는 SYSTEM',
    changed_at DATETIME(6)  NOT NULL COMMENT 'UTC',
    PRIMARY KEY (id),
    KEY idx_key_status_history_key_changed (key_id, changed_at),
    CONSTRAINT fk_key_status_history_key FOREIGN KEY (key_id) REFERENCES crypto_key (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '키 버전 상태 전이 이력 (append-only)';

-- ----------------------------------------------------------------------------
-- 6. key_usage_log — 암복호화·서명검증 테스트 호출 기록 (성공·실패 모두)
--    호출자(actor) 컬럼 없음 — 관리자 행위 추적은 3주차 audit_log 담당
-- ----------------------------------------------------------------------------
CREATE TABLE key_usage_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    key_id      BIGINT       NOT NULL,
    version     INT          NOT NULL,
    operation   VARCHAR(10)  NOT NULL COMMENT 'ENCRYPT | DECRYPT | SIGN | VERIFY',
    result      VARCHAR(10)  NOT NULL COMMENT 'SUCCESS | FAIL',
    fail_reason VARCHAR(200) NULL,
    used_at     DATETIME(6)  NOT NULL COMMENT 'UTC',
    PRIMARY KEY (id),
    KEY idx_key_usage_log_key_used (key_id, used_at),
    KEY idx_key_usage_log_key_version (key_id, version),
    CONSTRAINT fk_key_usage_log_key FOREIGN KEY (key_id) REFERENCES crypto_key (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '키 테스트 사용 이력 (버전별 사용 통계 원천)';

-- ----------------------------------------------------------------------------
-- 7. app_user — 서비스 사용자 (3주차 예정)
--    연락처·이메일은 마스터키 AES-256-GCM 암호화 후 Base64 저장,
--    평문 LIKE 불가 → HMAC 해시(phone_hash/email_hash)로 정확검색만 지원
--    응답은 마스킹(010-****-1234), 원문 조회는 ADMIN 한정 + 감사로그 기록
-- ----------------------------------------------------------------------------
CREATE TABLE app_user (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(50)  NOT NULL,
    password_hash  VARCHAR(60)  NOT NULL COMMENT 'BCrypt 해시 (salt 내장, 별도 컬럼 없음)',
    phone_enc      VARCHAR(500) NULL COMMENT 'Base64(마스터키 AES-256-GCM 암호문+태그)',
    phone_hash     VARCHAR(64)  NULL COMMENT 'HMAC-SHA256 16진수 — 정확검색용',
    email_enc      VARCHAR(500) NULL COMMENT 'Base64(마스터키 AES-256-GCM 암호문+태그)',
    email_hash     VARCHAR(64)  NULL COMMENT 'HMAC-SHA256 16진수 — 정확검색용',
    iv             VARCHAR(24)  NULL COMMENT '개인정보 암호화 IV(12바이트) Base64',
    enc_ver        INT          NOT NULL DEFAULT 1 COMMENT '어느 세대 마스터키로 암호화했는지 (기본 1)',
    status         VARCHAR(20)  NOT NULL COMMENT '사용자 상태 (예: ACTIVE | LOCKED)',
    integrity_hash VARCHAR(64)  NULL COMMENT 'HMAC-SHA256(name|password_hash|status|enc_ver)',
    created_at     DATETIME(6)  NOT NULL COMMENT 'UTC',
    updated_at     DATETIME(6)  NOT NULL COMMENT 'UTC',
    PRIMARY KEY (id),
    KEY idx_app_user_phone_hash (phone_hash),
    KEY idx_app_user_email_hash (email_hash)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '서비스 사용자 (개인정보 마스터키 암호화)';

-- ----------------------------------------------------------------------------
-- 8. notice — 공지사항 게시판 (4주차 예정)
-- ----------------------------------------------------------------------------
CREATE TABLE notice (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    title      VARCHAR(200) NOT NULL,
    content    TEXT         NOT NULL,
    expose_yn  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '노출 여부',
    view_count INT          NOT NULL DEFAULT 0,
    created_by VARCHAR(50)  NOT NULL COMMENT '작성 관리자 login_id',
    created_at DATETIME(6)  NOT NULL COMMENT 'UTC',
    updated_at DATETIME(6)  NOT NULL COMMENT 'UTC',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '공지사항';

-- ----------------------------------------------------------------------------
-- 9. notice_file — 공지 첨부파일 (4주차 예정)
--    파일 본문은 마스터키 AES-256-GCM 암호화 후 Base64 처리하여 저장/관리,
--    테이블에는 메타데이터와 IV만 보관
-- ----------------------------------------------------------------------------
CREATE TABLE notice_file (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    notice_id  BIGINT       NOT NULL,
    orig_name  VARCHAR(255) NOT NULL COMMENT '업로드 원본 파일명',
    saved_name VARCHAR(255) NOT NULL COMMENT '저장 파일명 (암호문 파일)',
    size       BIGINT       NOT NULL COMMENT '원본 크기(바이트)',
    iv         VARCHAR(24)  NOT NULL COMMENT '파일 암호화 IV(12바이트) Base64',
    enc_ver    INT          NOT NULL DEFAULT 1 COMMENT '어느 세대 마스터키로 암호화했는지 (기본 1)',
    created_at DATETIME(6)  NOT NULL COMMENT 'UTC',
    PRIMARY KEY (id),
    KEY idx_notice_file_notice (notice_id),
    CONSTRAINT fk_notice_file_notice FOREIGN KEY (notice_id) REFERENCES notice (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '공지 첨부파일 (마스터키로 암호화)';

-- ----------------------------------------------------------------------------
-- 10. audit_log — 관리자 행위 감사로그 (3주차 예정)
--     append-only (UPDATE/DELETE 금지 — 애플리케이션·DB 권한으로 통제)
--     해시 체인: row_hash = H(prev_hash + 현재 행 핵심 데이터), 최초 행 prev_hash = 'EMPTY'
-- ----------------------------------------------------------------------------
CREATE TABLE audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    actor       VARCHAR(50)  NOT NULL COMMENT '행위자 login_id 또는 SYSTEM',
    action      VARCHAR(50)  NOT NULL COMMENT '예: LOGIN, KEY_CREATED, KEY_REACTIVATED, KEY_TEST_ENCRYPT, USER_PLAIN_VIEWED',
    target_type VARCHAR(30)  NULL COMMENT '대상 종류 (예: KEY | USER | NOTICE)',
    target_id   VARCHAR(100) NULL COMMENT '대상 식별자 (key_uid 등)',
    detail      JSON         NULL COMMENT '행위 상세 (PostgreSQL jsonb 대응)',
    created_at  DATETIME(6)  NOT NULL COMMENT 'UTC',
    prev_hash   VARCHAR(64)  NOT NULL COMMENT '직전 행 row_hash, 최초 행은 EMPTY',
    row_hash    VARCHAR(64)  NOT NULL COMMENT 'H(prev_hash + 행 핵심 데이터) — 체인 검증용',
    PRIMARY KEY (id),
    KEY idx_audit_log_created (created_at),
    KEY idx_audit_log_target (target_type, target_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '관리자 행위 감사로그 (append-only, 해시 체인)';
