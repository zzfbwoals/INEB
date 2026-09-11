package com.ineb.kms.integrity;

import com.ineb.kms.audit.AuditChainScheduler;
import com.ineb.kms.audit.AuditHook;
import com.ineb.kms.domain.AppUser;
import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.KeyStatusHistory;
import com.ineb.kms.key.KeyIntegrityGuard;
import com.ineb.kms.repository.AppUserRepository;
import com.ineb.kms.repository.CryptoKeyRepository;
import com.ineb.kms.user.UserIntegrityHasher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 트리거 알림 묶음을 처리한다 — 스케줄러가 주기적으로 하던 일을 변경 즉시, 바뀐 행에 대해서만 수행 (2026-09-11).
 * <ol>
 *   <li>앱 자신의 연결(application_name = kms-backend)이 만든 알림은 버린다. 나머지가 "DB 직접 수정"이다.</li>
 *   <li>직접 수정마다 {@code DB_DIRECT_CHANGE}(actor SYSTEM, target = 화면 대상, detail = 테이블·연산·id·DB 사용자·응용 이름)를
 *       기록한다 — 해시가 없는 테이블(공지·첨부·이력·사용 로그·admin_user·crypto_config)도 흔적이 남고, SSE 로 해당 화면이 즉시
 *       다시 불러온다. 같은 묶음 안의 같은 (테이블, 연산, 대상)은 한 건으로 합친다(대량 UPDATE 스팸 방지).</li>
 *   <li>해시 대상은 이어서 재검증한다 — app_user 는 불일치면 USER_INTEGRITY_VIOLATION(표시 중이면 기록 없음), crypto_key·key_material 은
 *       {@link KeyIntegrityGuard#enforceOnRead}(자동 정지·표시), audit_log·audit_log_shadow 는 체인 즉시 재검증.</li>
 * </ol>
 * 값이 원복되어 해시가 다시 맞아도 여기서는 아무것도 지우지 않는다 — 위반 표시 해제는 관리자 재해시뿐({@link IntegrityFlagService}).
 * target 규칙: crypto_key·key_material·key_status_history·key_usage_log → KEY#{key_uid} · app_user → USER#{id} · notice·notice_file → NOTICE#{id} ·
 * admin_user → AUTH#{login_id} · crypto_config → CONFIG#{config_key} · audit_log·audit_log_shadow → AUDIT. 참조를 찾을 수 없으면(삭제된 키 등) {@code KEY#*} 처럼 와일드카드.
 */
@Component
public class IntegrityChangeHandler {

    public static final String DIRECT_CHANGE = "DB_DIRECT_CHANGE";

    private static final Logger log = LoggerFactory.getLogger(IntegrityChangeHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_IDS_IN_DETAIL = 20;

    /** 알림 한 건 */
    public record Change(String table, String op, String id, String ref, String app, String user) {

        static Change parse(String json) {
            try {
                JsonNode n = MAPPER.readTree(json);
                return new Change(text(n, "table"), text(n, "op"), text(n, "id"), text(n, "ref"), text(n, "app"), text(n, "user"));
            } catch (RuntimeException e) {
                return null;
            }
        }

        private static String text(JsonNode n, String field) {
            JsonNode v = n.get(field);
            return v == null || v.isNull() ? "" : v.asString();
        }

        boolean fromApp() {
            return IntegrityChangeTrigger.APP_NAME.equals(app);
        }
    }

    private final AppUserRepository userRepository;
    private final CryptoKeyRepository keyRepository;
    private final UserIntegrityHasher userHasher;
    private final KeyIntegrityGuard keyGuard;
    private final IntegrityFlagService flags;
    private final AuditChainScheduler chainScheduler;
    private final AuditHook auditHook;
    private volatile boolean ownNameApplied = true;

    public IntegrityChangeHandler(AppUserRepository userRepository, CryptoKeyRepository keyRepository,
                                  UserIntegrityHasher userHasher, KeyIntegrityGuard keyGuard, IntegrityFlagService flags,
                                  AuditChainScheduler chainScheduler, AuditHook auditHook) {
        this.userRepository = userRepository;
        this.keyRepository = keyRepository;
        this.userHasher = userHasher;
        this.keyGuard = keyGuard;
        this.flags = flags;
        this.chainScheduler = chainScheduler;
        this.auditHook = auditHook;
    }

    /** 리스너가 자기 연결의 application_name 확인 결과를 알려준다 — false 면 audit_log INSERT 알림을 무시(자기 기록 루프 방지) */
    public void setOwnNameApplied(boolean applied) {
        this.ownNameApplied = applied;
    }

    /**
     * 트랜잭션을 걸지 않는다 — 감사 기록(append)이 advisory xact lock 을 잡은 채로 체인 재검증(check → 별도 트랜잭션 appendDetached)을
     * 부르면 같은 스레드의 두 연결이 서로를 기다리는 교착이 생기고, 같은 트랜잭션 안에서는 방금 넣은 자기 기록을 원본·섀도 정밀도 차이로
     * "수정됨"으로 오인한다(2026-09-11 확인). 각 단계(기록·가드·재검증)는 저마다 트랜잭션을 가진다.
     */
    public void handleBatch(List<String> payloads) {
        List<Change> direct = new ArrayList<>();
        for (String payload : payloads) {
            Change c = Change.parse(payload);
            if (c == null) {
                log.warn("DB 변경 알림 형식 오류: {}", payload);
                continue;
            }
            if (c.fromApp()) {
                continue;
            }
            if (!ownNameApplied && "audit_log".equals(c.table()) && "INSERT".equals(c.op())) {
                continue;   // 앱 이름 미적용 환경 — 자기 감사 기록을 직접 수정으로 오인해 무한 루프가 되는 것을 막는다
            }
            direct.add(c);
        }
        if (direct.isEmpty()) {
            return;
        }
        recordDirectChanges(direct);
        reverify(direct);
    }

    /** 같은 (테이블, 연산, 대상) 은 한 건으로 합쳐 DB_DIRECT_CHANGE 기록 */
    private void recordDirectChanges(List<Change> direct) {
        Map<String, List<Change>> groups = new LinkedHashMap<>();
        Map<String, String> targets = new LinkedHashMap<>();
        for (Change c : direct) {
            String target = targetOf(c);
            String key = c.table() + "|" + c.op() + "|" + target;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
            targets.putIfAbsent(key, target);
        }
        for (Map.Entry<String, List<Change>> e : groups.entrySet()) {
            List<Change> list = e.getValue();
            Change first = list.getFirst();
            Set<String> ids = new LinkedHashSet<>();
            for (Change c : list) {
                if (!c.id().isEmpty()) {
                    ids.add(c.id());
                }
            }
            String idText = ids.isEmpty() ? "-" : String.join(",", ids.stream().limit(MAX_IDS_IN_DETAIL).toList())
                    + (ids.size() > MAX_IDS_IN_DETAIL ? "(+" + (ids.size() - MAX_IDS_IN_DETAIL) + ")" : "");
            String detail = "table=" + first.table() + ", op=" + first.op() + ", rows=" + list.size() + ", ids=" + idText
                    + ", dbUser=" + first.user() + ", app=" + (first.app().isEmpty() ? "-" : first.app());
            log.warn("DB 직접 수정 감지: {}", detail);
            auditHook.record(KeyStatusHistory.SYSTEM_ACTOR, DIRECT_CHANGE, targets.get(e.getKey()), detail);
        }
    }

    /** 해시·체인 대상은 바뀐 행만 즉시 재검증 (같은 키·사용자는 한 번만) */
    private void reverify(List<Change> direct) {
        Set<Long> users = new LinkedHashSet<>();
        Set<Long> keys = new LinkedHashSet<>();
        boolean chain = false;
        for (Change c : direct) {
            switch (c.table()) {
                case "app_user" -> parseLong(c.id()).ifPresent(users::add);
                case "crypto_key" -> parseLong(c.id()).ifPresent(keys::add);
                case "key_material" -> parseLong(c.ref()).ifPresent(keys::add);
                case "audit_log", "audit_log_shadow" -> chain = true;
                default -> { }
            }
        }
        for (Long id : users) {
            checkUser(id);
        }
        for (Long id : keys) {
            keyRepository.findById(id).ifPresent(keyGuard::enforceOnRead);
        }
        if (chain) {
            log.warn("audit_log 직접 수정 감지 — 체인 즉시 재검증");
            chainScheduler.checkNow();
        }
    }

    private void checkUser(Long id) {
        AppUser user = userRepository.findById(id).orElse(null);
        if (user == null || userHasher.verify(user)) {
            return;
        }
        if (flags.flag(AuditHook.userTarget(user.getId()), "integrity_hash 불일치 — DB 변경 알림으로 감지(자동 조치 없음, 재해시 전까지 유지)")) {
            log.warn("app_user 무결성 위반(변경 알림): id={}, name={}", user.getId(), user.getName());
        }
    }

    /** 화면 구독 규칙에 맞는 target — 참조를 못 찾으면 도메인 와일드카드 */
    String targetOf(Change c) {
        return switch (c.table()) {
            case "crypto_key" -> c.ref().isEmpty() ? "KEY#*" : AuditHook.keyTarget(c.ref());
            case "key_material", "key_status_history", "key_usage_log" -> parseLong(c.ref())
                    .flatMap(keyRepository::findById).map(CryptoKey::getKeyUid).map(AuditHook::keyTarget).orElse("KEY#*");
            case "app_user" -> c.id().isEmpty() ? "USER#*" : "USER#" + c.id();
            case "notice" -> c.id().isEmpty() ? "NOTICE#*" : "NOTICE#" + c.id();
            case "notice_file" -> c.ref().isEmpty() ? "NOTICE#*" : "NOTICE#" + c.ref();
            case "admin_user" -> c.ref().isEmpty() ? "AUTH#*" : AuditHook.authTarget(c.ref());
            case "crypto_config" -> c.id().isEmpty() ? "CONFIG#*" : "CONFIG#" + c.id();
            case "audit_log", "audit_log_shadow" -> "AUDIT";
            default -> "DB#" + c.table();
        };
    }

    private static java.util.Optional<Long> parseLong(String text) {
        try {
            return text == null || text.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(Long.parseLong(text.trim()));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }
}
