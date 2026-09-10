package com.ineb.kms.repository;

import com.ineb.kms.domain.KeyUsageLog;
import com.ineb.kms.domain.UsageOperation;
import com.ineb.kms.domain.UsageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KeyUsageLogRepository extends JpaRepository<KeyUsageLog, Long> {

    /** 대시보드 사용 추이 — 기간 내 전체(키 함께 로드) */
    @Query("select u from KeyUsageLog u join fetch u.key where u.usedAt >= :since")
    List<KeyUsageLog> findSince(@Param("since") Instant since);

    /** 대시보드 연산 실패 — 기간 내 FAIL 최신 20건 */
    List<KeyUsageLog> findFirst20ByResultAndUsedAtGreaterThanEqualOrderByUsedAtDescIdDesc(UsageResult result, Instant since);

    Page<KeyUsageLog> findByKeyIdOrderByUsedAtDescIdDesc(Long keyId, Pageable pageable);

    long countByKeyIdAndVersion(Long keyId, int version);

    Optional<KeyUsageLog> findTopByKeyIdAndVersionOrderByUsedAtDesc(Long keyId, int version);

    long countByKeyIdAndUsedAtAfter(Long keyId, Instant since);

    long countByKeyIdAndOperationAndUsedAtAfter(Long keyId, UsageOperation operation, Instant since);

    long countByKeyIdAndResultAndUsedAtAfter(Long keyId, UsageResult result, Instant since);

    /** 구 버전(현행이 아닌 버전)으로 처리된 복호화·검증 호출 수 */
    long countByKeyIdAndVersionNotAndOperationInAndUsedAtAfter(Long keyId, int currentVersion,
                                                               java.util.Collection<UsageOperation> operations, Instant since);
}
