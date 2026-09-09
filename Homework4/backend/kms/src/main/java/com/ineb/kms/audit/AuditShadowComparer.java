package com.ineb.kms.audit;

import com.ineb.kms.domain.AuditLog;
import com.ineb.kms.domain.AuditLogShadow;
import com.ineb.kms.domain.ChainRow;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.LongFunction;
import org.springframework.stereotype.Component;

/**
 * 원본(audit_log)과 섀도(audit_log_shadow)를 id 순으로 병합 순회해 "무엇이 달라졌는지"를 행 단위로 뽑는다.
 * - DELETED : 섀도에만 있음 (원본에서 지워짐 — 체인이 못 잡는 꼬리 삭제 포함)
 * - INSERTED: 원본에만 있음 (앱을 거치지 않고 DB 에 직접 끼워 넣음)
 * - MODIFIED: 양쪽에 있으나 필드가 다름 (달라진 필드 목록)
 * 같은 순회에서 원본 체인과 섀도 체인도 함께 검증하므로 추가 비용이 없다. 양쪽 다 없는 id(롤백으로 소비된 시퀀스)는 차이가 아니다.
 * 목록은 각 {@value #SAMPLE_MAX}건까지만 모으고 카운트는 전체를 센다.
 */
@Component
public class AuditShadowComparer {

    public static final int SAMPLE_MAX = 200;

    public record Modified(AuditLog current, AuditLogShadow original, List<String> fields) { }

    public record Result(AuditChainVerifier.Result chain, AuditChainVerifier.Result shadowChain,
                         long currentRows, long shadowRows,
                         long deletedCount, long insertedCount, long modifiedCount,
                         List<AuditLogShadow> deleted, List<AuditLog> inserted, List<Modified> modified) {

        public boolean shadowClean() {
            return deletedCount == 0 && insertedCount == 0 && modifiedCount == 0;
        }
    }

    private final AuditChainVerifier verifier;

    public AuditShadowComparer(AuditChainVerifier verifier) {
        this.verifier = verifier;
    }

    public Result compare(Iterator<? extends AuditLog> current, Iterator<? extends AuditLogShadow> shadow) {
        AuditChainVerifier.Walk chainWalk = verifier.walk();
        AuditChainVerifier.Walk shadowWalk = verifier.walk();
        List<AuditLogShadow> deleted = new ArrayList<>();
        List<AuditLog> inserted = new ArrayList<>();
        List<Modified> modified = new ArrayList<>();
        long deletedCount = 0;
        long insertedCount = 0;
        long modifiedCount = 0;
        long currentRows = 0;
        long shadowRows = 0;

        AuditLog a = current.hasNext() ? current.next() : null;
        AuditLogShadow s = shadow.hasNext() ? shadow.next() : null;
        while (a != null || s != null) {
            if (s == null || (a != null && a.getId() < s.getId())) {
                insertedCount++;
                if (inserted.size() < SAMPLE_MAX) {
                    inserted.add(a);
                }
                chainWalk.accept(a);
                currentRows++;
                a = current.hasNext() ? current.next() : null;
            } else if (a == null || s.getId() < a.getId()) {
                deletedCount++;
                if (deleted.size() < SAMPLE_MAX) {
                    deleted.add(s);
                }
                shadowWalk.accept(s);
                shadowRows++;
                s = shadow.hasNext() ? shadow.next() : null;
            } else {
                List<String> fields = diff(a, s);
                if (!fields.isEmpty()) {
                    modifiedCount++;
                    if (modified.size() < SAMPLE_MAX) {
                        modified.add(new Modified(a, s, fields));
                    }
                }
                chainWalk.accept(a);
                shadowWalk.accept(s);
                currentRows++;
                shadowRows++;
                a = current.hasNext() ? current.next() : null;
                s = shadow.hasNext() ? shadow.next() : null;
            }
        }
        return new Result(chainWalk.finish(), shadowWalk.finish(), currentRows, shadowRows,
                deletedCount, insertedCount, modifiedCount, deleted, inserted, modified);
    }

    /** 달라진 필드 이름 목록 — detail 은 저장된 암호문 문자열 그대로 비교한다(복호화 불필요) */
    static List<String> diff(ChainRow a, ChainRow b) {
        List<String> fields = new ArrayList<>();
        if (!Objects.equals(a.getActor(), b.getActor())) {
            fields.add("actor");
        }
        if (!Objects.equals(a.getAction(), b.getAction())) {
            fields.add("action");
        }
        if (!Objects.equals(a.getTarget(), b.getTarget())) {
            fields.add("target");
        }
        if (!Objects.equals(a.getDetail(), b.getDetail())) {
            fields.add("detail");
        }
        if (!Objects.equals(a.getPrevHash(), b.getPrevHash())) {
            fields.add("prevHash");
        }
        if (!Objects.equals(a.getRowHash(), b.getRowHash())) {
            fields.add("rowHash");
        }
        if (!Objects.equals(a.getCreatedAt(), b.getCreatedAt())) {
            fields.add("createdAt");
        }
        return fields;
    }

    /** id 오름차순 keyset 순회 — 전체를 한 번에 메모리에 올리지 않는다 (fetch: lastId 초과 행을 500건씩) */
    public static <T extends ChainRow> Iterator<T> keyset(LongFunction<List<T>> fetch) {
        return new Iterator<>() {
            private List<T> batch = fetch.apply(0);
            private int index = 0;

            @Override
            public boolean hasNext() {
                if (index < batch.size()) {
                    return true;
                }
                if (batch.isEmpty()) {
                    return false;
                }
                batch = fetch.apply(batch.getLast().getId());
                index = 0;
                return !batch.isEmpty();
            }

            @Override
            public T next() {
                return batch.get(index++);
            }
        };
    }
}
