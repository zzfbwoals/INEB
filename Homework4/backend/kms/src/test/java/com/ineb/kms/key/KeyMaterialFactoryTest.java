package com.ineb.kms.key;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ineb.kms.crypto.MasterKeyHolder;
import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.KeyAlgorithm;
import com.ineb.kms.domain.KeyMode;
import com.ineb.kms.domain.KeyPurpose;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KeyMaterialFactoryTest {

    private final byte[] masterKey = new byte[32];
    private KeyMaterialFactory factory;

    @BeforeEach
    void setUp() {
        for (int i = 0; i < masterKey.length; i++) {
            masterKey[i] = (byte) i;
        }
        MasterKeyHolder holder = mock(MasterKeyHolder.class);
        when(holder.getKey()).thenReturn(masterKey);
        factory = new KeyMaterialFactory(holder);
    }

    private CryptoKey key(KeyAlgorithm alg, int size, KeyMode mode) {
        return new CryptoKey("K", alg, size, mode, alg.defaultPurpose(), false, null, null);
    }

    @Test
    @DisplayName("대칭키 재료는 keySize/8 바이트이며 래핑 후 언래핑하면 같은 값이 복원된다")
    void symmetricRoundTrip() throws GeneralSecurityException {
        KeyMaterialFactory.Generated g = factory.generate(key(KeyAlgorithm.AES, 256, KeyMode.GCM));
        assertNull(g.publicKey());
        assertEquals(12, Base64.getDecoder().decode(g.iv()).length);
        byte[] plain = factory.unwrap(g.wrappedKey(), g.iv());
        assertEquals(32, plain.length);
        // 두 번 언래핑해도 동일
        assertArrayEquals(plain, factory.unwrap(g.wrappedKey(), g.iv()));
    }

    @Test
    @DisplayName("버전마다 서로 다른 난수 재료가 생성된다")
    void differentMaterialEachTime() throws GeneralSecurityException {
        CryptoKey k = key(KeyAlgorithm.SEED, 128, KeyMode.CBC);
        KeyMaterialFactory.Generated a = factory.generate(k);
        KeyMaterialFactory.Generated b = factory.generate(k);
        assertFalse(java.util.Arrays.equals(factory.unwrap(a.wrappedKey(), a.iv()), factory.unwrap(b.wrappedKey(), b.iv())));
    }

    @Test
    @DisplayName("HMAC 재료는 SHA256=32B, SHA512=64B 다")
    void hmacSizes() throws GeneralSecurityException {
        KeyMaterialFactory.Generated s256 = factory.generate(key(KeyAlgorithm.SHA256, 256, null));
        KeyMaterialFactory.Generated s512 = factory.generate(key(KeyAlgorithm.SHA512, 512, null));
        assertEquals(32, factory.unwrap(s256.wrappedKey(), s256.iv()).length);
        assertEquals(64, factory.unwrap(s512.wrappedKey(), s512.iv()).length);
    }

    @Test
    @DisplayName("RSA 는 개인키(PKCS#8)를 래핑하고 공개키(X.509)는 평문 Base64 로 제공한다")
    void rsaKeyPair() throws Exception {
        KeyMaterialFactory.Generated g = factory.generate(key(KeyAlgorithm.RSA, 2048, null));
        assertNotNull(g.publicKey());
        PublicKey pub = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(g.publicKey())));
        assertEquals("RSA", pub.getAlgorithm());
        byte[] priv = factory.unwrap(g.wrappedKey(), g.iv());
        assertEquals("RSA", KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(priv)).getAlgorithm());
    }

    @Test
    @DisplayName("ECDSA P-384 키쌍이 생성된다")
    void ecdsaKeyPair() throws Exception {
        KeyMaterialFactory.Generated g = factory.generate(key(KeyAlgorithm.ECDSA, 384, null));
        PublicKey pub = KeyFactory.getInstance("EC")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(g.publicKey())));
        assertEquals("EC", pub.getAlgorithm());
    }

    @Test
    @DisplayName("래핑 값이 변조되면 언래핑이 실패한다 (GCM 태그 불일치)")
    void tamperedWrapFails() {
        KeyMaterialFactory.Generated g = factory.generate(key(KeyAlgorithm.AES, 128, KeyMode.CBC));
        byte[] ct = Base64.getDecoder().decode(g.wrappedKey());
        ct[0] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(ct);
        assertThrows(GeneralSecurityException.class, () -> factory.unwrap(tampered, g.iv()));
    }
}
