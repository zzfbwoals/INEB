package com.ineb.kms.domain;

/** 상태 전이의 원인. key_status_history.trigger 에 기록되어 관리자 연산과 서버 자동 전이를 구분한다. */
public enum HistoryTrigger {
    /** 관리자 연산 (ACTIVATE / DEACTIVATE / DESTROY) */
    OPERATION,
    /** 활성일 도래 — 스케줄러 */
    DATE_REACHED,
    /** 갱신 주기 도래 — 스케줄러 자동 갱신 */
    SCHEDULE,
    /** integrity_hash 불일치 감지 — 자동 정지 */
    INTEGRITY,
    /** 무결성 정지 버전의 재활성화 */
    REACTIVATE,
    /** 갱신으로 새 버전 생성 (또는 예약 버전 취소) */
    ROTATE
}
