package com.ineb.kms.repository;

import com.ineb.kms.domain.AuditLog;
import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** audit_log 는 append-only — 저장과 조회만 사용하고 삭제·수정 메서드는 호출하지 않는다. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    Optional<AuditLog> findTopByOrderByIdDesc();

    /** 체인 검증용 keyset 순회 — id 오름차순 500건씩 */
    List<AuditLog> findFirst500ByIdGreaterThanOrderByIdAsc(long id);

    /** 대시보드 보안 신호 — 기간 내 (action, target) 만 프로젝션 (detail 암호문은 읽지 않는다) */
    @Query("select a.action, a.target from AuditLog a where a.createdAt >= :since")
    List<Object[]> findActionTargetSince(@Param("since") Instant since);

    /** 통합 검색 — 최신 500건(복호화 후 detail 까지 비교) */
    List<AuditLog> findFirst500ByOrderByIdDesc();
}
