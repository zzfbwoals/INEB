package com.ineb.kms.repository;

import com.ineb.kms.domain.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    Optional<AppUser> findByEmailHash(String emailHash);

    boolean existsByEmailHash(String emailHash);

    boolean existsByEmailHashAndIdNot(String emailHash, Long id);
}
