package com.ineb.kms.audit;

import com.ineb.kms.domain.AuditLog;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 체인 순차 검증. id 오름차순 전체를 훑으며 두 가지를 구분해 잡아낸다:
 * - TAMPERED: 행 내용이 바뀜 (저장된 row_hash 와 재계산 값 불일치)
 * - CHAIN_BROKEN: 연결이 끊김 (prev_hash 가 직전 행의 row_hash 와 다름 → 중간 삭제·삽입)
 * 다음 행 비교는 저장된 row_hash 를 기준으로 이어가므로 위반 하나가 이후 전체로 번지지 않고 구간으로 좁혀진다.
 * 연속한 같은 유형의 위반 행은 하나의 구간(fromId~toId)으로 묶어 반환한다.
 */
@Component
public class AuditChainVerifier {

    public enum ViolationType { TAMPERED, CHAIN_BROKEN }

    public record Violation(long fromId, long toId, ViolationType type) { }

    public record Result(boolean valid, long totalRows, List<Violation> violations) { }

    private final AuditHasher hasher;

    public AuditChainVerifier(AuditHasher hasher) {
        this.hasher = hasher;
    }

    public Result verify(Iterable<AuditLog> rowsInIdOrder) {
        List<Violation> violations = new ArrayList<>();
        String expectedPrev = AuditLog.CHAIN_ANCHOR;
        long totalRows = 0;

        for (AuditLog row : rowsInIdOrder) {
            totalRows++;
            if (!expectedPrev.equals(row.getPrevHash())) {
                add(violations, row.getId(), ViolationType.CHAIN_BROKEN);
            }
            if (!hasher.verifyRow(row)) {
                add(violations, row.getId(), ViolationType.TAMPERED);
            }
            expectedPrev = row.getRowHash();
        }
        return new Result(violations.isEmpty(), totalRows, violations);
    }

    /** 직전 위반과 같은 유형이고 id 가 이어지면 구간을 확장한다 */
    private static void add(List<Violation> violations, long id, ViolationType type) {
        if (!violations.isEmpty()) {
            Violation last = violations.getLast();
            if (last.type() == type && last.toId() == id - 1) {
                violations.set(violations.size() - 1, new Violation(last.fromId(), id, type));
                return;
            }
        }
        violations.add(new Violation(id, id, type));
    }
}
