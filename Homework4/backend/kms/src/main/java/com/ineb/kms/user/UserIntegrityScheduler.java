package com.ineb.kms.user;

import com.ineb.kms.audit.AuditHook;
import com.ineb.kms.domain.AppUser;
import com.ineb.kms.domain.KeyStatusHistory;
import com.ineb.kms.repository.AppUserRepository;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 사용자(app_user) 무결성 배치 검증 (키 무결성·감사 체인 배치와 같은 패턴, 2026-09-07).
 * <p>
 * 사용자 무결성은 조회 시점에 재계산해 {@code integrityValid} 플래그로만 응답하므로, DB 직접 변조는
 * 누군가 목록을 다시 불러오기 전까지 열린 화면에 반영되지 않았다. 그래서 주기적으로 전체 사용자를 검증하고
 * 위반 ↔ 정상 <b>상태가 바뀌는 사용자에 대해서만</b> 감사 기록(USER_INTEGRITY_VIOLATION / USER_INTEGRITY_RESTORED,
 * actor SYSTEM, target USER#id)을 남긴다. 기록은 체인 append 를 거쳐 SSE 로 브로드캐스트되고, 사용자 목록은
 * USER 접두 이벤트에 refetch 하므로 배지가 최대 한 주기 안에 자동 갱신된다. 지속 위반은 매 주기 기록하지 않는다.
 * <p>
 * 키와 달리 자동 조치(정지 등)는 없다 — 설계상 사용자 무결성 위반은 플래그 표시까지만.
 * 위반 집합은 메모리에만 두므로 재기동 직후 첫 주기에 기존 위반이 한 번 더 기록된다.
 */
@Component
public class UserIntegrityScheduler {

    private static final Logger log = LoggerFactory.getLogger(UserIntegrityScheduler.class);

    private final AppUserRepository repository;
    private final UserIntegrityHasher hasher;
    private final AuditHook auditHook;
    private final boolean enabled;

    /** 직전 주기에 위반으로 판정된 사용자 id — 상태 전이 판별용 */
    private final Set<Long> violated = new HashSet<>();

    public UserIntegrityScheduler(AppUserRepository repository, UserIntegrityHasher hasher, AuditHook auditHook,
                                  @Value("${kms.scheduler.user-integrity-check:true}") boolean enabled) {
        this.repository = repository;
        this.hasher = hasher;
        this.auditHook = auditHook;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${kms.scheduler.interval-ms:60000}", initialDelayString = "${kms.scheduler.initial-delay-ms:30000}")
    public void tick() {
        if (!enabled) {
            return;
        }
        try {
            int[] changed = sweep();
            if (changed[0] + changed[1] > 0) {
                log.info("사용자 무결성 배치: 신규 위반 {}건, 복구 {}건", changed[0], changed[1]);
            }
        } catch (RuntimeException e) {
            log.error("사용자 무결성 배치 검증 실패", e);
        }
    }

    /** @return {신규 위반 수, 복구 수}. 트랜잭션 없이 실행 — 감사 append 가 각자 REQUIRED 트랜잭션으로 커밋·브로드캐스트된다 */
    int[] sweep() {
        Set<Long> now = new HashSet<>();
        int newlyViolated = 0;
        for (AppUser user : repository.findAll()) {
            if (hasher.verify(user)) {
                continue;
            }
            now.add(user.getId());
            if (violated.add(user.getId())) {
                newlyViolated++;
                log.warn("app_user 무결성 위반: id={}, name={}", user.getId(), user.getName());
                auditHook.record(KeyStatusHistory.SYSTEM_ACTOR, "USER_INTEGRITY_VIOLATION",
                        AuditHook.userTarget(user.getId()), "integrity_hash 불일치 — 플래그 표시(자동 조치 없음)");
            }
        }
        int restored = 0;
        for (Long id : new HashSet<>(violated)) {
            if (!now.contains(id)) {
                violated.remove(id);
                restored++;
                log.info("app_user 무결성 정상 복구: id={}", id);
                auditHook.record(KeyStatusHistory.SYSTEM_ACTOR, "USER_INTEGRITY_RESTORED",
                        AuditHook.userTarget(id), "integrity_hash 일치 확인");
            }
        }
        return new int[] {newlyViolated, restored};
    }
}
