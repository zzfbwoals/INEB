package com.ineb.kms.repository;

import com.ineb.kms.domain.AuditViolation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** audit_violation 은 append-only — 저장과 조회만 쓰고 수정·삭제 메서드는 호출하지 않는다 */
public interface AuditViolationRepository extends JpaRepository<AuditViolation, Long> {

    boolean existsByAuditIdAndKindAndFields(Long auditId, String kind, String fields);

    List<AuditViolation> findAllByOrderByIdAsc();

    /** 감사 행 하나에 걸린 증거 전부 — 확인(acknowledge) 단위 */
    List<AuditViolation> findByAuditIdOrderByIdAsc(Long auditId);
}
