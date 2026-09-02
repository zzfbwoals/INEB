package com.ineb.kms.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // 로그인 실패는 계정 존재 여부를 노출하지 않도록 단일 메시지를 사용한다
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다."),
    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // ---- KMS 관리 키 (2주차) ----
    KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "키를 찾을 수 없습니다."),
    KEY_VERSION_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 버전을 찾을 수 없습니다."),
    KEY_NAME_DUPLICATE(HttpStatus.CONFLICT, "이미 사용 중인 키명입니다."),
    KEY_INVALID_ALGORITHM_PARAM(HttpStatus.BAD_REQUEST, "알고리즘·사이즈·모드·용도 조합이 올바르지 않습니다."),
    KEY_ROTATION_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "갱신 주기는 1~730일이어야 합니다."),
    KEY_ACTIVATION_DATE_NOT_EDITABLE(HttpStatus.BAD_REQUEST, "활성일은 준비(PRE_ACTIVE) 상태인 버전만 수정할 수 있습니다."),
    // 상태 전이 규칙 위반은 400, 현재 상태와의 충돌은 409
    KEY_TRANSITION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "허용되지 않는 상태 전이입니다."),
    KEY_STATE_CONFLICT(HttpStatus.CONFLICT, "현재 상태에서는 수행할 수 없는 연산입니다."),
    KEY_LATEST_VERSION_DEACTIVATE(HttpStatus.CONFLICT, "최신 버전은 단독으로 정지할 수 없습니다. 키 정지 또는 갱신 후 정지하세요."),
    KEY_REACTIVATE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "무결성 위반으로 자동 정지된 버전만 재활성화할 수 있습니다."),
    KEY_MATERIAL_CORRUPTED(HttpStatus.CONFLICT, "키 재료 언래핑에 실패했습니다. 재활성화할 수 없으며 갱신으로 새 버전을 생성하세요."),
    KEY_VERSION_LIMIT(HttpStatus.BAD_REQUEST, "버전 상한(100)에 도달했습니다. 사용하지 않는 구 버전을 삭제한 뒤 갱신하세요."),
    KEY_ACTIVE_EXISTS(HttpStatus.CONFLICT, "운영 중인 버전이 있어 키 전체를 삭제할 수 없습니다. 먼저 키를 정지하세요."),
    KEY_VERSION_NOT_USABLE(HttpStatus.BAD_REQUEST, "해당 버전은 현재 상태에서 사용할 수 없습니다."),
    KEY_PURPOSE_MISMATCH(HttpStatus.BAD_REQUEST, "키 용도에 맞지 않는 연산입니다."),
    KEY_PLAINTEXT_TOO_LONG(HttpStatus.BAD_REQUEST, "평문이 알고리즘의 최대 길이를 초과했습니다."),
    KEY_CIPHERTEXT_FORMAT(HttpStatus.BAD_REQUEST, "암호문(서명값) 형식이 올바르지 않습니다. 기대 형식: {version}:{iv}:{ciphertext}"),
    KEY_CRYPTO_FAILED(HttpStatus.BAD_REQUEST, "암호 연산에 실패했습니다. 암호문 손상 또는 키 불일치입니다."),
    KEY_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "무결성 위반이 감지되어 해당 버전이 자동 정지되었습니다."),

    // ---- 사용자 관리 (3주차) ----
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    USER_EMAIL_DUPLICATE(HttpStatus.CONFLICT, "이미 등록된 이메일입니다."),
    USER_PASSWORD_POLICY(HttpStatus.BAD_REQUEST, "비밀번호는 8자 이상이며 특수문자를 포함해야 합니다."),
    USER_DATA_CORRUPTED(HttpStatus.CONFLICT, "개인정보 암호문 복호화에 실패했습니다. 데이터 손상 여부를 확인하세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
