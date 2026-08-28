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
}
