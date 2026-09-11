package com.ineb.kms.integrity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ineb.kms.audit.AuditHasher;
import com.ineb.kms.audit.AuditHook;
import com.ineb.kms.domain.AuditLog;
import com.ineb.kms.repository.AuditLogRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 단위 테스트용 — 감사 기록을 메모리에 쌓고, 그 기록으로 위반 표시를 판정하는 {@link IntegrityFlagService} 를 만든다.
 * 운영에서는 DbAuditHook → audit_log → IntegrityFlagService 가 같은 테이블을 공유하므로, 테스트에서도
 * 가드·서비스가 남긴 기록을 flag 판정이 그대로 보도록 훅과 서비스를 한 쌍으로 제공한다.
 */
public final class IntegrityFlagTestSupport {

    private IntegrityFlagTestSupport() {
    }

    /** 테스트가 쓰는 훅(delegate 는 단언용 sink)과 그 기록을 읽는 위반 표시 서비스 */
    public record Fixture(AuditHook hook, IntegrityFlagService flags, List<AuditLog> rows) {
    }

    public static Fixture create(AuditHook delegate) {
        List<AuditLog> rows = new ArrayList<>();
        AuditHook hook = (actor, action, target, detail) -> {
            rows.add(new AuditLog(actor == null ? "SYSTEM" : actor, action, target, detail == null ? "" : detail,
                    "EMPTY", "ok", Instant.now()));
            delegate.record(actor, action, target, detail);
        };
        AuditLogRepository repository = mock(AuditLogRepository.class);
        when(repository.findByActionInAndTargetInOrderByIdAsc(anyCollection(), anyCollection()))
                .thenAnswer(inv -> filter(rows, inv.getArgument(0), inv.getArgument(1)));
        when(repository.findByActionInOrderByIdAsc(anyCollection()))
                .thenAnswer(inv -> filter(rows, inv.getArgument(0), null));
        AuditHasher hasher = mock(AuditHasher.class);
        when(hasher.verifyRow(any())).thenAnswer(inv -> "ok".equals(((AuditLog) inv.getArgument(0)).getRowHash()));
        return new Fixture(hook, new IntegrityFlagService(repository, hasher, hook), rows);
    }

    /** 위조 행 시뮬레이션 — row_hash 가 유효하지 않은 기록을 끼워 넣는다 */
    public static void insertForged(Fixture fixture, String action, String target) {
        fixture.rows().add(new AuditLog("hacker", action, target, "", "EMPTY", "forged", Instant.now()));
    }

    private static List<AuditLog> filter(List<AuditLog> rows, Collection<String> actions, Collection<String> targets) {
        List<AuditLog> out = new ArrayList<>();
        for (AuditLog row : rows) {
            if (actions.contains(row.getAction()) && (targets == null || targets.contains(row.getTarget()))) {
                out.add(row);
            }
        }
        return out;
    }
}
