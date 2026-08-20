package com.ineb.kms.repository;

import com.ineb.kms.domain.CryptoConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CryptoConfigRepository extends JpaRepository<CryptoConfig, String> {
}
