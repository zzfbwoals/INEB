package com.ineb.kms.user;

import com.ineb.kms.audit.AuditHook;
import com.ineb.kms.domain.AppUser;
import com.ineb.kms.integrity.IntegrityFlagService;
import com.ineb.kms.repository.AppUserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 사용자(app_user) 무결성 배치 검증 (키 무결성·감사 체인 배치와 같은 패턴, 2026-09-07).
 * <p>
 * 2026-09-11 개정: 실시간 감지는 DB 트리거 알림({@code integrity.IntegrityChangeListener})이 맡고, 이 배치는 알림을 놓친
 * 변경(앱 정지 중의 수정 등)을 잡는 안전망이다. 해시가 불일치하는데 아직 위반 표시가 없는 사용자에 대해서만
 * USER_INTEGRITY_VIOLATION(actor SYSTEM, target USER#id)을 남긴다. 위반 상태는 {@link IntegrityFlagService}(감사 체인 파생)가
 * 들고 있으므로 재기동해도 잊지 않고, 값이 원복되어 해시가 다시 일치해도 <b>자동 복구 기록은 남기지 않는다</b> —
 * 정상 복귀는 관리자의 재해시(USER_INTEGRITY_RESEALED)로만 가능하다. 키와 달리 자동 조치(정지 등)는 없다.
 */
@Component
public class UserIntegrityScheduler {

    private static final Logger log = LoggerFactory.getLogger(UserIntegrityScheduler.class);

    private final AppUserRepository repository;
    private final UserIntegrityHasher hasher;
    private final IntegrityFlagService flags;
    private final boolean enabled;

    public UserIntegrityScheduler(AppUserRepository repository, UserIntegrityHasher hasher, IntegrityFlagService flags,
                                  @Value("${kms.scheduler.user-integrity-check:true}") boolean enabled) {
        this.repository = repository;
        this.hasher = hasher;
        this.flags = flags;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${kms.scheduler.interval-ms:60000}", initialDelayString = "${kms.scheduler.initial-delay-ms:30000}")
    public void tick() {
        if (!enabled) {
            return;
        }
        try {
            int recorded = sweep();
            if (recorded > 0) {
                log.info("사용자 무결성 배치: 신규 위반 {}건", recorded);
            }
        } catch (RuntimeException e) {
            log.error("사용자 무결성 배치 검증 실패", e);
        }
    }

    /** @return 새로 위반 표시한 사용자 수. 트랜잭션 없이 실행 — 감사 append 가 각자 REQUIRED 트랜잭션으로 커밋·브로드캐스트된다 */
    int sweep() {
        List<AppUser> mismatched = new ArrayList<>();
        for (AppUser user : repository.findAll()) {
            if (!hasher.verify(user)) {
                mismatched.add(user);
            }
        }
        if (mismatched.isEmpty()) {
            return 0;
        }
        Set<String> already = flags.flagged(mismatched.stream().map(u -> AuditHook.userTarget(u.getId())).toList());
        int recorded = 0;
        for (AppUser user : mismatched) {
            String target = AuditHook.userTarget(user.getId());
            if (already.contains(target)) {
                continue;
            }
            log.warn("app_user 무결성 위반(배치): id={}, name={}", user.getId(), user.getName());
            flags.flag(target, "integrity_hash 불일치 — 배치 검증으로 감지(자동 조치 없음, 재해시 전까지 유지)");
            recorded++;
        }
        return recorded;
    }
}
