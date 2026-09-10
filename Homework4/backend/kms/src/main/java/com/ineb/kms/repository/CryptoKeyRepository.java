package com.ineb.kms.repository;

import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.KeyState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CryptoKeyRepository extends JpaRepository<CryptoKey, Long>, JpaSpecificationExecutor<CryptoKey> {

    Optional<CryptoKey> findByKeyUid(String keyUid);

    boolean existsByKeyName(String keyName);

    boolean existsByKeyNameAndIdNot(String keyName, Long id);

    /** 스케줄러: 자동 갱신 주기가 도래한 운영 중 키 */
    List<CryptoKey> findByAutoRotateTrueAndNextRotationAtLessThanEqualAndStatus(Instant now, KeyState status);

    long countByStatus(KeyState status);

    /** 대시보드 — 알고리즘 분포(폐기 제외) */
    List<CryptoKey> findByStatusNot(KeyState status);

    /** 대시보드 — 자동 갱신 키 중 다음 갱신일이 기한 이내인 키 (갱신 임박) */
    List<CryptoKey> findByAutoRotateTrueAndStatusAndNextRotationAtLessThanEqual(KeyState status, Instant until);
}
