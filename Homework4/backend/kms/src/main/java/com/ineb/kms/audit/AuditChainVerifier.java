package com.ineb.kms.audit;

import com.ineb.kms.domain.AuditLog;
import com.ineb.kms.domain.ChainRow;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 체인 순차 검증. id 오름차순 전체를 훑으며 두 가지를 구분해 잡아낸다:
 * - TAMPERED: 행 내용이 바뀜 (저장된 row_hash 와 재계산 값 불일치)
 * - CHAIN_BROKEN: 연결이 끊김 (prev_hash 가 직전 행의 row_hash 와 다름 → 중간 삭제·삽입)
 * 다음 행 비교는 저장된 row_hash 를 기준으로 이어가므로 위반 하나가 이후 전체로 번지지 않고 구간으로 좁혀진다.
 * 연속한 같은 유형의 위반 행은 하나의 구간(fromId~toId)으로 묶어 반환한다.
 * <p>
 * {@link Walk} 는 스트리밍 형태 — 원본과 섀도를 한 번에 병합 순회하는 AuditShadowComparer 가 행을 하나씩 흘려 넣는다.
 * 원본만 검증할 때는 {@link #verify(Iterable)} 를 쓴다.
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

    public Result verify(Iterable<? extends ChainRow> rowsInIdOrder) {
        Walk walk = walk();
        for (ChainRow row : rowsInIdOrder) {
            walk.accept(row);
        }
        return walk.finish();
    }

    public Walk walk() {
        return new Walk();
    }

    /** 행을 id 순으로 하나씩 받아 검증 상태를 누적한다 — 한 순회에서 여러 체인을 동시에 검증할 수 있다 */
    public final class Walk {
        private final List<Violation> violations = new ArrayList<>();
        private String expectedPrev = AuditLog.CHAIN_ANCHOR;
        private long totalRows = 0;

        private Walk() {
        }

        public void accept(ChainRow row) {
            totalRows++;
            if (!expectedPrev.equals(row.getPrevHash())) {
                add(row.getId(), ViolationType.CHAIN_BROKEN);
            }
            if (!hasher.verifyRow(row)) {
                add(row.getId(), ViolationType.TAMPERED);
            }
            expectedPrev = row.getRowHash();
        }

        public Result finish() {
            return new Result(violations.isEmpty(), totalRows, List.copyOf(violations));
        }

        /** 직전 위반과 같은 유형이고 id 가 이어지면 구간을 확장한다 */
        private void add(long id, ViolationType type) {
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
}
