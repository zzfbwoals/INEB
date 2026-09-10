package com.ineb.kms.repository;

import com.ineb.kms.domain.KeyMaterial;
import com.ineb.kms.domain.KeyState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KeyMaterialRepository extends JpaRepository<KeyMaterial, Long> {

    List<KeyMaterial> findByKeyIdOrderByVersionDesc(Long keyId);

    Optional<KeyMaterial> findByKeyIdAndVersion(Long keyId, int version);

    List<KeyMaterial> findByKeyIdAndState(Long keyId, KeyState state);

    long countByKeyId(Long keyId);

    /** 대시보드 — 상태별 버전 수 */
    long countByState(KeyState state);

    /** 대시보드 — 해당 상태이면서 current_version 이 아닌 버전 수 (ACTIVE 로 호출하면 구 버전 복호화 전용 수) */
    @Query("select count(m) from KeyMaterial m where m.state = :state and m.version <> m.key.currentVersion")
    long countByStateAndVersionNotCurrent(@Param("state") KeyState state);

    /** 스케줄러: 활성일이 도래한 준비 버전 */
    List<KeyMaterial> findByStateAndActivationDateLessThanEqual(KeyState state, Instant now);

    /** 무결성 배치 검증 대상 (재료가 남아 있는 버전) */
    List<KeyMaterial> findByStateNot(KeyState state);
}
