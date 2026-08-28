package com.ineb.kms.domain;

/**
 * KMS 관리 키 버전(key_material)의 상태. KMIP 2.1 §4.57 의 6종에서 Compromised 계열을 제외한 4종.
 * 상태의 주체는 버전이며, 키(crypto_key)의 status 는 버전 상태에서 파생된다.
 *
 * <ul>
 *   <li>PRE_ACTIVE  — 재료는 생성·래핑됐으나 활성일 미도래. 암복호화·서명검증 불가</li>
 *   <li>ACTIVE      — 정상. 암호화·서명은 최신 버전(current_version)만, 복호화·검증은 모든 ACTIVE 버전</li>
 *   <li>DEACTIVATED — 암·복호화·서명·검증 전면 차단. 재료는 보존. 무결성 위반 자동 정지 버전만 재활성화 가능</li>
 *   <li>DESTROYED   — 재료(wrapped_key) 물리 삭제. 메타·이력은 보존되는 종단 상태</li>
 * </ul>
 */
public enum KeyState {
    PRE_ACTIVE,
    ACTIVE,
    DEACTIVATED,
    DESTROYED
}
