package com.ineb.kms.repository;

import com.ineb.kms.domain.KeyUsageLog;
import com.ineb.kms.domain.UsageOperation;
import com.ineb.kms.domain.UsageResult;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KeyUsageLogRepository extends JpaRepository<KeyUsageLog, Long> {

    Page<KeyUsageLog> findByKeyIdOrderByUsedAtDescIdDesc(Long keyId, Pageable pageable);

    long countByKeyIdAndVersion(Long keyId, int version);

    Optional<KeyUsageLog> findTopByKeyIdAndVersionOrderByUsedAtDesc(Long keyId, int version);

    long countByKeyIdAndUsedAtAfter(Long keyId, Instant since);

    long countByKeyIdAndOperationAndUsedAtAfter(Long keyId, UsageOperation operation, Instant since);

    long countByKeyIdAndResultAndUsedAtAfter(Long keyId, UsageResult result, Instant since);

    /** 구 버전(현행이 아닌 버전)으로 처리된 호출 수 */
    long countByKeyIdAndVersionNotAndUsedAtAfter(Long keyId, int currentVersion, Instant since);
}
