package com.ineb.kms.key;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.domain.KeyAlgorithm;
import com.ineb.kms.domain.KeyMode;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KeyCipherSupportTest {

    private static final byte[] MSG = "고객 카드번호 테스트 데이터 4111-1111-1111-1111".getBytes(StandardCharsets.UTF_8);

    @BeforeAll
    static void bc() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static byte[] key(int bits) {
        byte[] k = new byte[bits / 8];
        new SecureRandom().nextBytes(k);
        return k;
    }

    @ParameterizedTest(name = "{0}-{1}/{2}")
    @CsvSource({
            "AES,256,GCM", "AES,128,CBC", "AES,192,CTR", "AES,256,ECB",
            "ARIA,256,GCM", "ARIA,128,CBC", "ARIA,256,CTR", "ARIA,128,ECB",
            "LEA,256,GCM", "LEA,128,CBC", "LEA,192,CTR", "LEA,256,ECB",
            "SEED,128,CBC", "SEED,128,ECB"})
    @DisplayName("대칭 알고리즘·모드별 암복호화 라운드트립")
    void symmetricRoundTrip(String alg, int bits, String mode) {
        KeyAlgorithm a = KeyAlgorithm.valueOf(alg);
        KeyMode m = KeyMode.valueOf(mode);
        byte[] k = key(bits);
        KeyCipherSupport.Encrypted enc = KeyCipherSupport.encryptSymmetric(a, m, k, MSG);
        assertEquals(m == KeyMode.ECB ? 0 : m == KeyMode.GCM ? 12 : 16, enc.iv().length);
        assertArrayEquals(MSG, KeyCipherSupport.decryptSymmetric(a, m, k, enc.iv(), enc.cipherText()));
    }

    @Test
    @DisplayName("다른 키로 복호화하면 실패(GCM 태그·패딩 오류)한다")
    void wrongKeyFails() {
        byte[] k = key(256);
        KeyCipherSupport.Encrypted enc = KeyCipherSupport.encryptSymmetric(KeyAlgorithm.AES, KeyMode.GCM, k, MSG);
        BusinessException e = assertThrows(BusinessException.class,
                () -> KeyCipherSupport.decryptSymmetric(KeyAlgorithm.AES, KeyMode.GCM, key(256), enc.iv(), enc.cipherText()));
        assertEquals(ErrorCode.KEY_CRYPTO_FAILED, e.getErrorCode());
    }

    @Test
    @DisplayName("RSA-OAEP 암복호화와 SHA256withRSA 서명·검증이 동작하고 평문 상한은 190B(2048) 다")
    void rsa() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        String pub = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        byte[] priv = pair.getPrivate().getEncoded();

        assertEquals(190, KeyCipherSupport.maxPlaintextBytes(KeyAlgorithm.RSA, 2048));
        assertEquals(-1, KeyCipherSupport.maxPlaintextBytes(KeyAlgorithm.AES, 256));

        byte[] ct = KeyCipherSupport.encryptRsa(pub, MSG);
        assertArrayEquals(MSG, KeyCipherSupport.decryptRsa(priv, ct));

        byte[] sig = KeyCipherSupport.sign(KeyAlgorithm.RSA, 2048, priv, MSG);
        assertTrue(KeyCipherSupport.verify(KeyAlgorithm.RSA, 2048, null, pub, MSG, sig));
        assertFalse(KeyCipherSupport.verify(KeyAlgorithm.RSA, 2048, null, pub, "다른 원문".getBytes(), sig));
        assertFalse(KeyCipherSupport.verify(KeyAlgorithm.RSA, 2048, null, pub, MSG, new byte[]{1, 2, 3}));
    }

    @Test
    @DisplayName("ECDSA P-256 / P-384 서명·검증")
    void ecdsa() throws Exception {
        for (int size : new int[]{256, 384}) {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec(size == 384 ? "secp384r1" : "secp256r1"));
            KeyPair pair = gen.generateKeyPair();
            String pub = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
            byte[] sig = KeyCipherSupport.sign(KeyAlgorithm.ECDSA, size, pair.getPrivate().getEncoded(), MSG);
            assertTrue(KeyCipherSupport.verify(KeyAlgorithm.ECDSA, size, null, pub, MSG, sig));
            assertFalse(KeyCipherSupport.verify(KeyAlgorithm.ECDSA, size, null, pub, "x".getBytes(), sig));
        }
    }

    @Test
    @DisplayName("HMAC-SHA256/512 는 재계산 비교로 검증한다")
    void hmac() {
        byte[] k = key(256);
        byte[] mac = KeyCipherSupport.sign(KeyAlgorithm.SHA256, 256, k, MSG);
        assertEquals(32, mac.length);
        assertTrue(KeyCipherSupport.verify(KeyAlgorithm.SHA256, 256, k, null, MSG, mac));
        assertFalse(KeyCipherSupport.verify(KeyAlgorithm.SHA256, 256, key(256), null, MSG, mac));
        assertEquals(64, KeyCipherSupport.sign(KeyAlgorithm.SHA512, 512, key(512), MSG).length);
    }
}
