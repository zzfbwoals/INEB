package com.ineb.kms.repository;

import com.ineb.kms.domain.Notice;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeRepository extends JpaRepository<Notice, Long>, JpaSpecificationExecutor<Notice> {

    /** 상세 조회 시 조회수 +1 — 엔티티를 거치지 않는 갱신이라 updated_at 이 바뀌지 않고, 동시 조회에도 누락이 없다 */
    @Modifying(clearAutomatically = true)
    @Query("update Notice n set n.viewCount = n.viewCount + 1 where n.id = :id")
    int increaseViewCount(@Param("id") Long id);

    /** 대시보드 */
    long countByPinnedTrue();

    long countByCreatedAtGreaterThanEqual(Instant since);
}
