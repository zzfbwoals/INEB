package com.ineb.kms.domain;

/**
 * 버전이 DEACTIVATED 로 들어간 원인. INTEGRITY 인 버전만 REACTIVATE 가 허용된다
 * (관리자가 의도적으로 정지한 버전은 KMIP 원칙대로 재활성화하지 않고 갱신으로 대체).
 */
public enum DeactivationTrigger {
    OPERATION,
    INTEGRITY
}
