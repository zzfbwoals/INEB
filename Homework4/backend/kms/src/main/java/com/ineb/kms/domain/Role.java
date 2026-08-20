package com.ineb.kms.domain;

/** 관리자 권한. 개인정보 원문 조회 등 민감 기능은 ADMIN 한정. */
public enum Role {
    ADMIN,
    OPERATOR
}
