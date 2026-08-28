package com.ineb.kms.repository;

import com.ineb.kms.domain.KeyStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KeyStatusHistoryRepository extends JpaRepository<KeyStatusHistory, Long> {

    List<KeyStatusHistory> findByKeyIdOrderByChangedAtDescIdDesc(Long keyId);
}
