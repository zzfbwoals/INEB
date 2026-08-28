package com.ineb.kms.domain;

/**
 * 상태 변경 연산. 상태는 이 연산의 결과로만 변한다 (클라이언트가 목표 상태를 직접 지정할 수 없음 — KMIP §4.57).
 */
public enum KeyAction {
    /** PRE_ACTIVE 버전 즉시 활성화 */
    ACTIVATE,
    /** 무결성 위반으로 자동 정지된 버전 복구 (언래핑 검증 → 해시 재계산 → ACTIVE) */
    REACTIVATE,
    /** 버전 또는 키 전체 정지 (암복호화·서명검증 전면 차단) */
    DEACTIVATE,
    /** 갱신 — 새 버전 생성. 구 버전은 ACTIVE 유지 */
    ROTATE,
    /** 삭제 — 재료 물리 파기, 메타·이력 보존 */
    DESTROY
}
