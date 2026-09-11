package com.ineb.kms.repository;

import com.ineb.kms.domain.AuditLog;
import java.time.Instant;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** audit_log 는 append-only — 저장과 조회만 사용하고 삭제·수정 메서드는 호출하지 않는다. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    Optional<AuditLog> findTopByOrderByIdDesc();

    /** 필터 콤보박스용 — 기록에 존재하는 행위자(distinct, 정렬) */
    @Query("select distinct a.actor from AuditLog a order by a.actor")
    List<String> findDistinctActors();

    /** 체인 검증용 keyset 순회 — id 오름차순 500건씩 */
    List<AuditLog> findFirst500ByIdGreaterThanOrderByIdAsc(long id);

    /** 대시보드 보안 신호 — 기간 내 (action, target) 만 프로젝션 (detail 암호문은 읽지 않는다) */
    @Query("select a.action, a.target from AuditLog a where a.createdAt >= :since")
    List<Object[]> findActionTargetSince(@Param("since") Instant since);

    /** 무결성 위반 표시 파생용 — 대상별 VIOLATION/RESEALED 기록을 id 순으로 (마지막 기록이 현재 상태) */
    List<AuditLog> findByActionInAndTargetInOrderByIdAsc(Collection<String> actions, Collection<String> targets);

    /** 무결성 위반 표시 파생용(전체) — 대시보드 집계 */
    List<AuditLog> findByActionInOrderByIdAsc(Collection<String> actions);

    /** 감사 체인 배치의 직전 판정 복원용 — AUDIT_CHAIN_VIOLATION / RESTORED 중 마지막 기록 (재기동 후 중복 기록 방지) */
    Optional<AuditLog> findTopByActionInOrderByIdDesc(Collection<String> actions);

    /** 통합 검색 — 최신 500건(복호화 후 detail 까지 비교) */
    List<AuditLog> findFirst500ByOrderByIdDesc();
}
