package com.ineb.kms.repository;

import com.ineb.kms.domain.KeyMaterial;
import com.ineb.kms.domain.KeyState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KeyMaterialRepository extends JpaRepository<KeyMaterial, Long> {

    List<KeyMaterial> findByKeyIdOrderByVersionDesc(Long keyId);

    Optional<KeyMaterial> findByKeyIdAndVersion(Long keyId, int version);

    List<KeyMaterial> findByKeyIdAndState(Long keyId, KeyState state);

    long countByKeyId(Long keyId);

    /** 스케줄러: 활성일이 도래한 준비 버전 */
    List<KeyMaterial> findByStateAndActivationDateLessThanEqual(KeyState state, Instant now);

    /** 무결성 배치 검증 대상 (재료가 남아 있는 버전) */
    List<KeyMaterial> findByStateNot(KeyState state);
}
