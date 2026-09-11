package com.ineb.kms.audit;

import com.ineb.kms.domain.AuditLog;
import com.ineb.kms.domain.AuditLogShadow;
import com.ineb.kms.domain.AuditViolation;
import com.ineb.kms.domain.ChainRow;
import com.ineb.kms.repository.AuditViolationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그 변조 증거 저장 (2026-09-11). 섀도 비교 결과에서 아직 기록되지 않은 삭제·삽입·수정 행을 스냅샷으로 남긴다.
 * 검증(check)은 읽기 전용 트랜잭션이므로 별도 트랜잭션(REQUIRES_NEW)으로 커밋한다. 이미 있는 (행, 종류, 바뀐 컬럼) 조합은 다시 넣지 않는다.
 */
@Service
public class AuditViolationStore {

    private final AuditViolationRepository repository;

    public AuditViolationStore(AuditViolationRepository repository) {
        this.repository = repository;
    }

    /** 새로 발견된 증거만 저장하고 돌려준다 (없으면 빈 목록) */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<AuditViolation> recordNew(AuditShadowComparer.Result result) {
        Instant now = Instant.now();
        List<AuditViolation> added = new ArrayList<>();
        for (AuditLogShadow s : result.deleted()) {
            add(added, AuditViolation.DELETED, "", s, now);
        }
        for (AuditLog a : result.inserted()) {
            add(added, AuditViolation.INSERTED, "", a, now);
        }
        for (AuditShadowComparer.Modified m : result.modified()) {
            add(added, AuditViolation.MODIFIED, String.join(",", m.fields()), m.current(), now);
        }
        return added;
    }

    private void add(List<AuditViolation> added, String kind, String fields, ChainRow snapshot, Instant now) {
        if (repository.existsByAuditIdAndKindAndFields(snapshot.getId(), kind, fields)) {
            return;
        }
        added.add(repository.save(new AuditViolation(kind, fields, snapshot, now)));
    }

    @Transactional(readOnly = true)
    public List<AuditViolation> all() {
        return repository.findAllByOrderByIdAsc();
    }
}
