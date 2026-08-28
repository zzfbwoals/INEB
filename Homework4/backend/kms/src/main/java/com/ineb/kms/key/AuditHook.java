package com.ineb.kms.key;

/**
 * 관리자 행위·시스템 이벤트 감사 기록 연결점. 2주차는 로그 구현체만 두고, 3주차에 audit_log 해시 체인 구현체로 교체한다.
 * 예약 행위유형: KEY_CREATED / KEY_UPDATED / KEY_STATUS_CHANGED / KEY_ROTATED / KEY_REACTIVATED / KEY_DESTROYED /
 * KEY_INTEGRITY_VIOLATION / KEY_TEST_ENCRYPT · DECRYPT · SIGN · VERIFY
 */
public interface AuditHook {

    void record(String actor, String action, String target, String detail);
}
