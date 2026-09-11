package com.ineb.kms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * app_user 스키마 보정 — `ddl-auto: update` 는 컬럼을 지우지 않으므로 기동 시 직접 처리한다.
 * <p>
 * phone_hash(HMAC 정확검색용) 는 2026-09-10 연락처·이메일 검색이 서버 측 복호화 후 부분일치로 바뀌면서 폐기됐다.
 * NOT NULL 컬럼이 남아 있으면 엔티티가 값을 주지 않는 INSERT 가 실패하므로 반드시 제거해야 한다.
 * `DROP COLUMN IF EXISTS` 라 매 기동 실행해도 무해(no-op).
 */
@Component
public class AppUserSchemaMigration implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(AppUserSchemaMigration.class);

    private final JdbcTemplate jdbc;

    public AppUserSchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void afterSingletonsInstantiated() {
        jdbc.execute("ALTER TABLE app_user DROP COLUMN IF EXISTS phone_hash");
        log.info("app_user.phone_hash 컬럼 제거 확인 (연락처 검색은 복호화 후 부분일치)");
    }
}
