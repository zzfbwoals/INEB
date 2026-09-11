package com.ineb.kms.integrity;

import com.ineb.kms.audit.AuditHasher;
import com.ineb.kms.audit.AuditHook;
import com.ineb.kms.domain.AuditLog;
import com.ineb.kms.domain.KeyStatusHistory;
import com.ineb.kms.repository.AuditLogRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 무결성 "위반 표시"의 근거 저장소 — 행(app_user·crypto_key)이 아니라 append-only 감사 체인에서 파생한다 (2026-09-11).
 * <p>
 * 해시 재검증은 "지금 값이 봉인값과 같은가"만 답하므로, DB 를 직접 고쳐 위반을 만든 뒤 원래 값으로 되돌리면
 * 해시가 다시 일치해 정상으로 보였다. 이제 위반이 한 번 감지되면 {@code *_INTEGRITY_VIOLATION} 을 남기고,
 * 그 대상은 관리자가 사유와 함께 재해시({@code *_INTEGRITY_RESEALED})하기 전까지 값과 무관하게 위반으로 취급한다.
 * 판정은 대상별 두 행위의 <b>마지막 기록</b>이 무엇인지로 정한다.
 * <ul>
 *   <li>행에 플래그 컬럼을 두면 DB 접근자가 컬럼을 되돌려 흔적 없이 정상이 되므로 채택하지 않았다.</li>
 *   <li>감사 로그에서 표시를 지우려면 VIOLATION 행을 지우거나 RESEALED 행을 끼워 넣어야 하는데, 삭제는 체인·섀도 비교로
 *       드러나고, 끼워 넣은 RESEALED 행은 무결성 HMAC 키 없이는 row_hash 를 만들 수 없어 {@link AuditHasher#verifyRow}
 *       검증에 걸린다(검증 실패 = 위반 유지).</li>
 * </ul>
 * 한계: DB 계정이 owner 하나라 VIOLATION 행 삭제 자체는 막지 못한다 — 그 경우 체인 위반이 대신 표시된다.
 */
@Service
public class IntegrityFlagService {

    public static final String KEY_VIOLATION = "KEY_INTEGRITY_VIOLATION";
    public static final String KEY_RESEALED = "KEY_INTEGRITY_RESEALED";
    public static final String USER_VIOLATION = "USER_INTEGRITY_VIOLATION";
    public static final String USER_RESEALED = "USER_INTEGRITY_RESEALED";

    private static final List<String> ACTIONS = List.of(KEY_VIOLATION, KEY_RESEALED, USER_VIOLATION, USER_RESEALED);

    private final AuditLogRepository repository;
    private final AuditHasher hasher;
    private final AuditHook auditHook;

    public IntegrityFlagService(AuditLogRepository repository, AuditHasher hasher, AuditHook auditHook) {
        this.repository = repository;
        this.hasher = hasher;
        this.auditHook = auditHook;
    }

    /** 주어진 대상 중 현재 위반 표시 중인 것 — 목록 한 페이지를 한 번의 조회로 판정한다 */
    public Set<String> flagged(Collection<String> targets) {
        if (targets.isEmpty()) {
            return Set.of();
        }
        return fold(repository.findByActionInAndTargetInOrderByIdAsc(ACTIONS, new LinkedHashSet<>(targets)));
    }

    /** 전체 위반 표시 대상 — 대시보드 집계용 */
    public Set<String> flaggedAll() {
        return fold(repository.findByActionInOrderByIdAsc(ACTIONS));
    }

    public boolean isFlagged(String target) {
        return flagged(List.of(target)).contains(target);
    }

    /**
     * 조회 경로(목록·상세·대시보드)용 스냅샷 — 대상 집합의 위반 표시를 한 번에 읽고, 해시 불일치를 <b>관찰하는 순간</b> 위반을
     * 기록한다. 트리거 리스너·배치가 기록하기 전에 화면이 먼저 불일치를 보는 틈(리스너 없는 인스턴스, 배치 주기 전, 재접속 공백)에서
     * 원복하면 정상으로 돌아가던 문제를 막는다 (2026-09-11). 조회는 읽기 전용 트랜잭션이라 기록은 별도 트랜잭션(REQUIRES_NEW)로 남긴다.
     */
    public Snapshot snapshot(Collection<String> targets) {
        return new Snapshot(flagged(targets));
    }

    /** 전체 대상 스냅샷 — 대시보드 집계용 */
    public Snapshot snapshotAll() {
        return new Snapshot(flaggedAll());
    }

    public final class Snapshot {
        private final Set<String> flagged;

        private Snapshot(Set<String> flagged) {
            this.flagged = new HashSet<>(flagged);
        }

        /** 최종 판정 = 해시 일치 && 위반 표시 없음. 불일치를 처음 보면 기록하고(같은 응답 안 중복 없음) 그때부터 표시가 유지된다 */
        public boolean check(String target, boolean hashValid, String detail) {
            if (!hashValid && !flagged.contains(target)) {
                auditHook.recordDetached(KeyStatusHistory.SYSTEM_ACTOR, violationAction(target), target, detail);
                flagged.add(target);
            }
            return hashValid && !flagged.contains(target);
        }

        public boolean isFlagged(String target) {
            return flagged.contains(target);
        }
    }

    /**
     * 위반 기록 — 이미 위반 표시 중이면 남기지 않는다(지속 위반 반복 기록 방지). 호출자 트랜잭션에 참여하며,
     * 기록은 체인 append → 커밋 후 SSE 브로드캐스트로 이어져 열린 화면이 즉시 갱신된다.
     * @return 새로 기록했으면 true
     */
    public boolean flag(String target, String detail) {
        if (isFlagged(target)) {
            return false;
        }
        auditHook.record(KeyStatusHistory.SYSTEM_ACTOR, violationAction(target), target, detail);
        return true;
    }

    /** 관리자 재해시 기록 — 호출자가 해시를 다시 계산·저장한 같은 트랜잭션에서 남긴다. 위반→정상의 유일한 경로 */
    public void reseal(String target, String actor, String detail) {
        auditHook.record(actor, resealAction(target), target, detail);
    }

    static String violationAction(String target) {
        return target.startsWith("KEY#") ? KEY_VIOLATION : USER_VIOLATION;
    }

    static String resealAction(String target) {
        return target.startsWith("KEY#") ? KEY_RESEALED : USER_RESEALED;
    }

    /** id 순 기록을 접어 대상별 마지막 기록으로 판정. RESEALED 는 row_hash 가 유효할 때만 해제로 인정한다 */
    private Set<String> fold(List<AuditLog> rows) {
        Map<String, AuditLog> last = new HashMap<>();
        for (AuditLog row : rows) {
            last.put(row.getTarget(), row);
        }
        Set<String> flagged = new HashSet<>();
        for (Map.Entry<String, AuditLog> e : last.entrySet()) {
            AuditLog row = e.getValue();
            boolean resealed = KEY_RESEALED.equals(row.getAction()) || USER_RESEALED.equals(row.getAction());
            if (!resealed || !hasher.verifyRow(row)) {
                flagged.add(e.getKey());
            }
        }
        return flagged;
    }
}
