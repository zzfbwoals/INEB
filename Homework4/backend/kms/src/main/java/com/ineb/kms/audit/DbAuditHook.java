package com.ineb.kms.audit;

import org.springframework.stereotype.Component;

/** 3주차 구현체 — 감사 이벤트를 audit_log 해시 체인에 기록한다 (2주차 LoggingAuditHook 대체). */
@Component
public class DbAuditHook implements AuditHook {

    private final AuditChainService chainService;

    public DbAuditHook(AuditChainService chainService) {
        this.chainService = chainService;
    }

    @Override
    public void record(String actor, String action, String target, String detail) {
        chainService.append(actor, action, target, detail);
    }
}
