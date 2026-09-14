package com.ineb.kms.audit.dto;

/**
 * detail 은 복호화한 원문. detailDecrypted=false 면 복호화 실패(암호화 이전 평문 행이거나 변조된 암호문).
 * prevHash·rowHash 는 위반 상세(diff)에서 해시 변조를 보여주기 위해 함께 내려준다(비밀 아님, 화면은 축약 표시).
 */
public record AuditLogItem(
        long id,
        String actor,
        String action,
        String target,
        String detail,
        boolean detailDecrypted,
        String createdAt,
        String prevHash,
        String rowHash) {
}
