package com.ineb.kms.repository;

import com.ineb.kms.domain.AuditLogShadow;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** audit_log_shadow — 조회(keyset 순회)와 네이티브 INSERT 만 사용한다. 수정·삭제는 트리거가 막는다. */
public interface AuditLogShadowRepository extends JpaRepository<AuditLogShadow, Long> {

    /** 비교·검증용 keyset 순회 — id 오름차순 500건씩 (원본 리포지토리와 같은 시그니처) */
    List<AuditLogShadow> findFirst500ByIdGreaterThanOrderByIdAsc(long id);

    /**
     * 원본 행을 같은 id 로 복사한다. assigned id 엔티티를 save 하면 merge(SELECT 선행)가 되고 PK 충돌 시 호출자
     * 트랜잭션(관리자 작업)까지 실패하므로, 충돌은 무시하고 기존 섀도 행을 증거로 남긴다(반환 0 = 충돌).
     */
    @Modifying
    @Query(value = "INSERT INTO audit_log_shadow (id, actor, action, target, detail, prev_hash, row_hash, created_at) "
            + "VALUES (:id, :actor, :action, :target, :detail, :prevHash, :rowHash, :createdAt) ON CONFLICT (id) DO NOTHING",
            nativeQuery = true)
    int insertIgnore(@Param("id") Long id, @Param("actor") String actor, @Param("action") String action,
                     @Param("target") String target, @Param("detail") String detail,
                     @Param("prevHash") String prevHash, @Param("rowHash") String rowHash,
                     @Param("createdAt") Instant createdAt);
}
