package com.ineb.kms.repository;

import com.ineb.kms.domain.AppUser;
import com.ineb.kms.domain.UserStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    Optional<AppUser> findByEmailHash(String emailHash);

    boolean existsByEmailHash(String emailHash);

    boolean existsByEmailHashAndIdNot(String emailHash, Long id);

    /** 대시보드 */
    long countByStatus(UserStatus status);

    long countByCreatedAtGreaterThanEqual(Instant since);
}
