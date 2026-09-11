package com.ineb.kms.integrity;

import com.ineb.kms.audit.AuditHook;
import com.ineb.kms.domain.KeyStatusHistory;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * DB 직접 수정 실시간 감지용 트리거 (2026-09-11). 모든 테이블에 AFTER 행 트리거(INSERT/UPDATE/DELETE)와 문장 트리거(TRUNCATE)를
 * 걸어 행이 바뀔 때마다 {@code pg_notify('kms_integrity', json)} 을 보낸다. 앱의 {@link IntegrityChangeListener} 가 LISTEN 으로
 * 받아 즉시 처리하므로 스케줄러(60초)를 기다리지 않고 화면에 반영된다.
 * <p>
 * 알림 JSON: {@code {"table","op","id","ref","app","user"}} — {@code app} 은 세션의 application_name 으로, 앱 자신의 연결은
 * {@link #APP_NAME} 을 달고 있어 앱이 한 저장은 걸러내고 그 밖의 세션(psql·IDE 등)의 변경만 "직접 수정"으로 취급한다.
 * {@code ref} 는 화면 대상(target)을 만들기 위한 참조 — crypto_key.key_uid · key_material/key_status_history/key_usage_log.key_id ·
 * notice_file.notice_id · admin_user.login_id. notice_file 은 enc_data 가 커서 행 전체를 JSON 으로 만들지 않고 컬럼만 읽는다.
 * <p>
 * 트리거는 막지 않고 알리기만 한다(변조 시연 가능). DB 계정이 owner 라 끌 수 있으므로 기동 시와 60초마다 tgenabled 를 확인해
 * DISABLED/MISSING 이면 {@code INTEGRITY_TRIGGER_TAMPERED} 를 기록한 뒤 다시 설치한다(멱등, PostgreSQL 14+ CREATE OR REPLACE TRIGGER).
 * 한계: 직접 수정 세션이 application_name 을 앱과 같게 위장하면 걸러지지 않는다 — 신호이지 증명이 아니며, 해시·체인이 최종 판정이다.
 */
@Component
public class IntegrityChangeTrigger implements SmartInitializingSingleton {

    public enum Status { ACTIVE, DISABLED, MISSING }

    /** 앱 연결의 application_name — DataSourceConfig 가 풀 연결마다 설정한다 */
    public static final String APP_NAME = "kms-backend";
    public static final String CHANNEL = "kms_integrity";
    static final String FUNCTION = "kms_integrity_notify";
    static final String PREFIX = "kms_integrity_notify_";
    static final String TRUNCATE_SUFFIX = "_trunc";
    /** 감시 대상 — 설계 [표 6-1] 10개 테이블 + 감사 복사본 */
    public static final List<String> TABLES = List.of("crypto_config", "admin_user", "crypto_key", "key_material",
            "key_status_history", "key_usage_log", "app_user", "notice", "notice_file", "audit_log", "audit_log_shadow");

    private static final Logger log = LoggerFactory.getLogger(IntegrityChangeTrigger.class);

    private final JdbcTemplate jdbc;
    private final AuditHook auditHook;
    private final boolean enabled;

    public IntegrityChangeTrigger(JdbcTemplate jdbc, AuditHook auditHook,
                                  @Value("${kms.integrity.listen:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.auditHook = auditHook;
        this.enabled = enabled;
    }

    /** 기동 시(스키마 생성 뒤) — 해제 흔적을 먼저 기록하고 재설치 */
    @Override
    public void afterSingletonsInstantiated() {
        if (!enabled) {
            return;
        }
        Status before = status();
        if (before == Status.DISABLED) {
            recordTampered("기동 시 비활성 발견");
        }
        install();
        log.info("DB 변경 알림 트리거 설치 완료 (이전 상태: {}, 대상 {}개 테이블)", before, TABLES.size());
    }

    /** 감시 — 운영 중 트리거가 꺼지거나 지워지면 기록 후 재설치 (실시간 감지의 공백을 배치 주기로 제한) */
    @Scheduled(fixedDelayString = "${kms.scheduler.interval-ms:60000}", initialDelayString = "${kms.scheduler.initial-delay-ms:30000}")
    public void watch() {
        if (!enabled) {
            return;
        }
        try {
            Status now = status();
            if (now != Status.ACTIVE) {
                recordTampered("운영 중 " + now + " 발견");
                install();
            }
        } catch (RuntimeException e) {
            log.error("DB 변경 알림 트리거 감시 실패", e);
        }
    }

    private void recordTampered(String how) {
        log.warn("DB 변경 알림 트리거 해제 흔적 — INTEGRITY_TRIGGER_TAMPERED 기록 후 재설치 ({})", how);
        auditHook.record(KeyStatusHistory.SYSTEM_ACTOR, "INTEGRITY_TRIGGER_TAMPERED", "AUDIT",
                "kms_integrity_notify 트리거 " + how + " — 재설치");
    }

    /** 멱등 — 함수·트리거를 다시 만든다 */
    public void install() {
        jdbc.execute("CREATE OR REPLACE FUNCTION " + FUNCTION + "() RETURNS trigger LANGUAGE plpgsql AS $$ "
                + "DECLARE rid text := ''; ref text := ''; rec jsonb; BEGIN "
                + "IF TG_OP = 'TRUNCATE' THEN rid := ''; "
                + "ELSIF TG_TABLE_NAME = 'notice_file' THEN "
                + "  IF TG_OP = 'DELETE' THEN rid := OLD.id::text; ref := OLD.notice_id::text; "
                + "  ELSE rid := NEW.id::text; ref := NEW.notice_id::text; END IF; "
                + "ELSE "
                + "  IF TG_OP = 'DELETE' THEN rec := to_jsonb(OLD); ELSE rec := to_jsonb(NEW); END IF; "
                + "  rid := COALESCE(rec->>'id', rec->>'config_key', ''); "
                + "  ref := COALESCE(rec->>'key_uid', rec->>'key_id', rec->>'notice_id', rec->>'login_id', ''); "
                + "END IF; "
                + "PERFORM pg_notify('" + CHANNEL + "', json_build_object("
                + "'table', TG_TABLE_NAME, 'op', TG_OP, 'id', rid, 'ref', ref, "
                + "'app', COALESCE(current_setting('application_name', true), ''), 'user', session_user)::text); "
                + "RETURN NULL; END $$");
        for (String table : TABLES) {
            jdbc.execute("CREATE OR REPLACE TRIGGER " + PREFIX + table + " AFTER INSERT OR UPDATE OR DELETE ON " + table
                    + " FOR EACH ROW EXECUTE FUNCTION " + FUNCTION + "()");
            jdbc.execute("CREATE OR REPLACE TRIGGER " + PREFIX + table + TRUNCATE_SUFFIX + " AFTER TRUNCATE ON " + table
                    + " FOR EACH STATEMENT EXECUTE FUNCTION " + FUNCTION + "()");
        }
    }

    /** pg_trigger 의 tgenabled — 'O' 정상, 'D' 비활성. 모든 트리거(테이블당 2개)가 'O' 여야 ACTIVE */
    public Status status() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT tgname, tgenabled FROM pg_trigger WHERE NOT tgisinternal AND tgname LIKE '" + PREFIX + "%'");
        if (rows.size() < TABLES.size() * 2) {
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
