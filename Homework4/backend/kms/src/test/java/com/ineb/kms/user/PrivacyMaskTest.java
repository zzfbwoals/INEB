package com.ineb.kms.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PrivacyMaskTest {

    @Test
    @DisplayName("연락처는 앞 3자리만 남기고 전부 마스킹한다 (구분자 유지)")
    void phone() {
        assertEquals("010-****-****", PrivacyMask.phone("010-1234-5678"));
        assertEquals("010-***-****", PrivacyMask.phone("010-123-5678"));
        assertEquals("010********", PrivacyMask.phone("01012345678"));
        assertEquals("", PrivacyMask.phone(null));
    }

    @Test
    @DisplayName("이메일은 @ 와 . 만 남기고 고정 개수 별표로 마스킹한다 (글자 수 비노출)")
    void email() {
        assertEquals("****@****.**.**", PrivacyMask.email("user@ineb.co.kr"));
        assertEquals("****@****.**", PrivacyMask.email("ab@x.com"));
        assertEquals("****@****.**", PrivacyMask.email("veryverylonglocal@gmail.com"));   // 길이가 달라도 동일
        assertEquals("****", PrivacyMask.email("이메일아님"));
        assertEquals("", PrivacyMask.email(""));
    }
}
