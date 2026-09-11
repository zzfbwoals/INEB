package com.ineb.kms.domain;

import java.time.Instant;

/** 해시 체인 검증 대상 행 — audit_log 와 그 섀도(audit_log_shadow)가 같은 검증기를 쓰기 위한 공통 시그니처 */
public interface ChainRow {

    Long getId();

    String getActor();

    String getAction();

    String getTarget();

    String getDetail();

    String getPrevHash();

    String getRowHash();

    Instant getCreatedAt();
}
