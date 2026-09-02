package com.ineb.kms.audit;

/**
 * 관리자 행위·시스템 이벤트 감사 기록 연결점. 3주차부터 {@link DbAuditHook}(audit_log 해시 체인)이 구현체다.
 * 행위유형: LOGIN_SUCCESS · LOGIN_FAILED · LOGOUT /
 * KEY_CREATED · KEY_UPDATED · KEY_STATUS_CHANGED · KEY_ROTATED · KEY_REACTIVATED · KEY_DESTROYED ·
 * KEY_INTEGRITY_VIOLATION · KEY_MATERIAL_VIEWED · KEY_TEST_ENCRYPT · DECRYPT · SIGN · VERIFY /
 * USER_CREATED · USER_UPDATED · USER_PLAIN_VIEWED /
 * AUDIT_CHAIN_VERIFIED · AUDIT_EXPORTED · AUDIT_CHAIN_VIOLATION · AUDIT_CHAIN_RESTORED(배치 검증, actor SYSTEM)
 * target 형식(2026-09-01 확정): KEY#{keyUid} / USER#{id} / AUTH#{loginId} / AUDIT — 아래 헬퍼로만 만든다.
 */
public interface AuditHook {

    void record(String actor, String action, String target, String detail);

    static String keyTarget(String keyUid) {
        return "KEY#" + keyUid;
    }

    static String userTarget(Long id) {
        return "USER#" + id;
    }

    static String authTarget(String loginId) {
        return "AUTH#" + loginId;
    }
}
