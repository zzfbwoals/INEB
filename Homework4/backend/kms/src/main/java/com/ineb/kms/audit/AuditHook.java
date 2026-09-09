package com.ineb.kms.audit;

/**
 * 관리자 행위·시스템 이벤트 감사 기록 연결점. 3주차부터 {@link DbAuditHook}(audit_log 해시 체인)이 구현체다.
 * 행위유형: LOGIN_SUCCESS · LOGIN_FAILED · LOGOUT /
 * KEY_CREATED · KEY_UPDATED · KEY_STATUS_CHANGED · KEY_ROTATED · KEY_REACTIVATED · KEY_DESTROYED ·
 * KEY_INTEGRITY_VIOLATION · KEY_MATERIAL_VIEWED · KEY_TEST_ENCRYPT · DECRYPT · SIGN · VERIFY /
 * USER_CREATED · USER_UPDATED · USER_PLAIN_VIEWED · USER_INTEGRITY_VIOLATION · USER_INTEGRITY_RESTORED(배치 검증, actor SYSTEM) /
 * AUDIT_CHAIN_VERIFIED · AUDIT_EXPORTED · AUDIT_CHAIN_VIOLATION · AUDIT_CHAIN_RESTORED(배치 검증, actor SYSTEM) ·
 * AUDIT_SHADOW_BACKFILLED(섀도 최초 복사, 경계 표식) · AUDIT_SHADOW_GUARD_TAMPERED(섀도 보호 트리거 해제 흔적) /
 * NOTICE_CREATED · NOTICE_UPDATED · NOTICE_DELETED · NOTICE_FILE_DOWNLOADED · NOTICE_FILE_DELETED
 * (첨부파일 행위도 target 은 소속 공지 — 상세 화면 SSE 매칭·감사 필터를 한 target 으로 묶는다)
 * target 형식(2026-09-01 확정): KEY#{keyUid} / USER#{id} / AUTH#{loginId} / NOTICE#{id} / AUDIT — 아래 헬퍼로만 만든다.
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

    static String noticeTarget(Long id) {
        return "NOTICE#" + id;
    }
}
