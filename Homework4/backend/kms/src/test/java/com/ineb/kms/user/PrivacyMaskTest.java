package com.ineb.kms.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PrivacyMaskTest {

    @Test
    @DisplayName("연락처는 가운데 자리만 마스킹한다: 010-****-5678")
    void phone() {
        assertEquals("010-****-5678", PrivacyMask.phone("010-1234-5678"));
        assertEquals("010-****-5678", PrivacyMask.phone("010-123-5678"));
        assertEquals("****5678", PrivacyMask.phone("01012345678"));   // 하이픈 없는 형식은 뒤 4자리만
        assertEquals("", PrivacyMask.phone(null));
    }

    @Test
    @DisplayName("이메일은 로컬 앞 2자만 남긴다: us****@ineb.co.kr")
    void email() {
        assertEquals("us****@ineb.co.kr", PrivacyMask.email("user@ineb.co.kr"));
        assertEquals("a****@x.com", PrivacyMask.email("ab@x.com"));   // 로컬 2자 이하면 1자만
        assertEquals("****", PrivacyMask.email("이메일아님"));
        assertEquals("", PrivacyMask.email(""));
    }
}
