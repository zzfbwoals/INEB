package com.ineb.kms.repository;

import com.ineb.kms.domain.AuditLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** audit_log 는 append-only — 저장과 조회만 사용하고 삭제·수정 메서드는 호출하지 않는다. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    Optional<AuditLog> findTopByOrderByIdDesc();

    /** 체인 검증용 keyset 순회 — id 오름차순 500건씩 */
    List<AuditLog> findFirst500ByIdGreaterThanOrderByIdAsc(long id);
}
