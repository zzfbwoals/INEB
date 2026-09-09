package com.ineb.kms.audit;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * audit_log_shadow 보호 트리거 — UPDATE/DELETE(행)·TRUNCATE(문장)를 예외로 막는다.
 * DB 계정이 owner 라 트리거를 끌 수는 있지만, 우발적 수정을 막고 "해제 흔적"(DISABLED/MISSING)을 탐지 신호로 쓴다.
 * 원본 audit_log 에는 트리거를 걸지 않는다 — 변조 시연이 가능해야 하고 섀도가 그 원본을 보여주는 구조다.
 */
@Component
public class AuditShadowGuard {

    public enum Status { ACTIVE, DISABLED, MISSING }

    static final String FUNCTION = "audit_log_shadow_guard";
    static final String TRG_ROW = "audit_log_shadow_no_update_delete";
    static final String TRG_TRUNCATE = "audit_log_shadow_no_truncate";

    private final JdbcTemplate jdbc;

    public AuditShadowGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 멱등 — 함수·트리거를 다시 만든다 (PostgreSQL 14+ CREATE OR REPLACE TRIGGER) */
    public void install() {
        jdbc.execute("CREATE OR REPLACE FUNCTION " + FUNCTION + "() RETURNS trigger LANGUAGE plpgsql AS $$ "
                + "BEGIN RAISE EXCEPTION 'audit_log_shadow is append-only: % blocked', TG_OP "
                + "USING ERRCODE = 'integrity_constraint_violation'; END $$");
        jdbc.execute("CREATE OR REPLACE TRIGGER " + TRG_ROW + " BEFORE UPDATE OR DELETE ON audit_log_shadow "
                + "FOR EACH ROW EXECUTE FUNCTION " + FUNCTION + "()");
        jdbc.execute("CREATE OR REPLACE TRIGGER " + TRG_TRUNCATE + " BEFORE TRUNCATE ON audit_log_shadow "
                + "FOR EACH STATEMENT EXECUTE FUNCTION " + FUNCTION + "()");
    }

    /** pg_trigger 의 tgenabled — 'O' 정상, 'D' 비활성. 두 트리거가 모두 'O' 여야 ACTIVE */
    public Status status() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT tgname, tgenabled FROM pg_trigger "
                        + "WHERE tgrelid = 'audit_log_shadow'::regclass AND NOT tgisinternal "
                        + "AND tgname IN ('" + TRG_ROW + "', '" + TRG_TRUNCATE + "')");
        if (rows.size() < 2) {
            return Status.MISSING;
        }
        for (Map<String, Object> row : rows) {
            if (!"O".equals(String.valueOf(row.get("tgenabled")))) {
                return Status.DISABLED;
            }
        }
        return Status.ACTIVE;
    }
}
