package com.ineb.kms.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PersonalDataCodecTest {

    private PersonalDataCodec codec;

    @BeforeEach
    void setUp() {
        byte[] masterKey = new byte[32];
        byte[] integrityKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            masterKey[i] = (byte) i;
            integrityKey[i] = (byte) (i + 100);
        }
        MasterKeyHolder holder = mock(MasterKeyHolder.class);
        when(holder.getKey()).thenReturn(masterKey);
        WrappedSecretStore store = mock(WrappedSecretStore.class);
        when(store.integrityKey()).thenReturn(integrityKey);
        codec = new PersonalDataCodec(holder, store);
    }

    @Test
    @DisplayName("암호화 후 복호화하면 원문이 복원된다 (한글 포함)")
    void roundTrip() {
        assertEquals("010-1234-5678", codec.decrypt(codec.encrypt("010-1234-5678")));
        assertEquals("홍길동@example.com", codec.decrypt(codec.encrypt("홍길동@example.com")));
    }

    @Test
    @DisplayName("같은 평문도 매번 다른 암호문이 나온다 (필드마다 새 랜덤 IV 동봉)")
    void freshIvEachTime() {
        String a = codec.encrypt("user@ineb.co.kr");
        String b = codec.encrypt("user@ineb.co.kr");
        assertNotEquals(a, b);
        // 선두 12바이트(IV)도 달라야 한다
        byte[] blobA = Base64.getDecoder().decode(a);
        byte[] blobB = Base64.getDecoder().decode(b);
        assertNotEquals(java.util.Arrays.toString(java.util.Arrays.copyOf(blobA, 12)),
                java.util.Arrays.toString(java.util.Arrays.copyOf(blobB, 12)));
    }

    @Test
    @DisplayName("암호문이 변조되면 USER_DATA_CORRUPTED 로 실패한다 (GCM 태그 불일치)")
    void tamperFails() {
        byte[] blob = Base64.getDecoder().decode(codec.encrypt("010-1234-5678"));
        blob[blob.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(blob);
        BusinessException e = assertThrows(BusinessException.class, () -> codec.decrypt(tampered));
        assertEquals(ErrorCode.USER_DATA_CORRUPTED, e.getErrorCode());
    }

    @Test
    @DisplayName("연락처 해시는 하이픈·공백 유무와 무관하게 같다 (숫자만 정규화)")
    void phoneHashNormalized() {
        assertEquals(codec.phoneHash("010-1234-5678"), codec.phoneHash("01012345678"));
        assertEquals(codec.phoneHash("010-1234-5678"), codec.phoneHash("010 1234 5678"));
        assertNotEquals(codec.phoneHash("010-1234-5678"), codec.phoneHash("010-1234-5679"));
    }

    @Test
    @DisplayName("이메일 해시는 대소문자·양끝 공백과 무관하게 같다 (소문자 정규화)")
    void emailHashNormalized() {
        assertEquals(codec.emailHash("User@Ineb.co.kr"), codec.emailHash(" user@ineb.co.kr "));
        assertNotEquals(codec.emailHash("user@ineb.co.kr"), codec.emailHash("user2@ineb.co.kr"));
    }
}
