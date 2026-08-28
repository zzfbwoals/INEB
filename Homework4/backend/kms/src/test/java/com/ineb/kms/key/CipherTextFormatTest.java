package com.ineb.kms.key;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CipherTextFormatTest {

    @Test
    @DisplayName("암호문은 version:iv:ct 로 인코딩되고 그대로 파싱된다 (IV 없으면 빈 자리)")
    void cipherRoundTrip() {
        String s = CipherTextFormat.encodeCipher(3, new byte[]{1, 2}, new byte[]{9, 8, 7});
        CipherTextFormat.Cipher c = CipherTextFormat.parseCipher(s);
        assertEquals(3, c.version());
        assertArrayEquals(new byte[]{1, 2}, c.iv());
        assertArrayEquals(new byte[]{9, 8, 7}, c.cipherText());

        String noIv = CipherTextFormat.encodeCipher(12, null, new byte[]{5});
        assertEquals("12::BQ==", noIv);
        assertEquals(0, CipherTextFormat.parseCipher(noIv).iv().length);
    }

    @Test
    @DisplayName("서명값은 version:sig 로 인코딩·파싱된다")
    void signatureRoundTrip() {
        String s = CipherTextFormat.encodeSignature(2, new byte[]{7, 7});
        assertEquals(2, CipherTextFormat.parseSignature(s).version());
        assertArrayEquals(new byte[]{7, 7}, CipherTextFormat.parseSignature(s).signature());
    }

    @Test
    @DisplayName("구분자 누락·버전 비숫자·Base64 오류·빈 암호문은 형식 오류 400 이다")
    void invalidFormats() {
        for (String bad : new String[]{null, "", "abc", "1:iv", "x:aa:bb", "1:aa:bb:cc", "1:!!:bb", "1:aa:", "99999:aa:bb"}) {
            BusinessException e = assertThrows(BusinessException.class, () -> CipherTextFormat.parseCipher(bad), bad);
            assertEquals(ErrorCode.KEY_CIPHERTEXT_FORMAT, e.getErrorCode());
        }
        assertThrows(BusinessException.class, () -> CipherTextFormat.parseSignature("1:aa:bb"));
    }
}
