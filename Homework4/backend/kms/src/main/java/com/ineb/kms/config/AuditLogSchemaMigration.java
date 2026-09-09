package com.ineb.kms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * audit_log.detail 을 varchar(500) → text 로 넓힌다 (2026-09-09 detail 마스터키 암호화 — 암호문은 base64 라 평문보다 길다).
 * <p>
 * `ddl-auto: update` 는 컬럼을 추가만 하고 기존 컬럼 타입은 바꾸지 않으므로, 이미 테이블이 있는 DB(개발 서버·로컬)는 여기서
 * 기동 시 한 번 바꾼다. 같은 타입으로의 ALTER 는 PostgreSQL 에서 무해(no-op)라 매 기동마다 실행해도 된다.
 * Hibernate DDL(EntityManagerFactory 초기화) 이후, 웹 서버가 요청을 받기 전에 실행되도록 SmartInitializingSingleton 을 쓴다.
 */
@Component
public class AuditLogSchemaMigration implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(AuditLogSchemaMigration.class);

    private final JdbcTemplate jdbc;

    public AuditLogSchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void afterSingletonsInstantiated() {
        jdbc.execute("ALTER TABLE audit_log ALTER COLUMN detail TYPE text");
        log.info("audit_log.detail 컬럼 타입 text 확인 (마스터키 암호문 저장)");
    }
}
