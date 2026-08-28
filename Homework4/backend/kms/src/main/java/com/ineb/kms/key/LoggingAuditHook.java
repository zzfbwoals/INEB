package com.ineb.kms.key;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 2주차 임시 구현 — 감사 이벤트를 애플리케이션 로그로만 남긴다. 3주차에 audit_log 구현체로 대체. */
@Component
public class LoggingAuditHook implements AuditHook {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    @Override
    public void record(String actor, String action, String target, String detail) {
        log.info("actor={} action={} target={} detail={}", actor, action, target, detail);
    }
}
