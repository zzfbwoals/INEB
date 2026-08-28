package com.ineb.kms.key;

import com.ineb.kms.crypto.AesGcmSupport;
import com.ineb.kms.crypto.CryptoConstants;
import com.ineb.kms.crypto.MasterKeyHolder;
import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.KeyAlgorithm;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 키 재료 생성과 마스터키 래핑. 버전마다 새 난수(또는 새 키쌍)를 만든다.
 * - 대칭: SecureRandom keySize/8 바이트 · HMAC: SHA256→32B, SHA512→64B
 * - 비대칭: 개인키 PKCS#8 바이트를 재료로 래핑, 공개키 X.509 는 Base64 평문 보관
 * - 래핑: AES-256-GCM(마스터키, 랜덤 IV 12B) → base64(ct+tag) / base64(iv) — WrappedSecretStore 와 같은 봉투 패턴
 * 평문 재료는 byte[] 로만 다루고 사용 직후 zeroize 한다. String 변환 금지.
 */
@Component
public class KeyMaterialFactory {

    /** 래핑 결과. 평문 재료는 포함하지 않는다. */
    public record Generated(String wrappedKey, String iv, String publicKey) { }

    private final MasterKeyHolder masterKeyHolder;
    private final SecureRandom random = new SecureRandom();

    public KeyMaterialFactory(MasterKeyHolder masterKeyHolder) {
        this.masterKeyHolder = masterKeyHolder;
    }

    public Generated generate(CryptoKey key) {
        byte[] material = null;
        try {
            String publicKey = null;
            switch (key.getAlgorithm().getKind()) {
                case SYMMETRIC -> material = randomBytes(key.getKeySize() / 8);
                case HMAC -> material = randomBytes(key.getAlgorithm() == KeyAlgorithm.SHA512 ? 64 : 32);
                case ASYMMETRIC -> {
                    KeyPair pair = generateKeyPair(key.getAlgorithm(), key.getKeySize());
                    material = pair.getPrivate().getEncoded();
                    publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
                }
            }
            return wrap(material, publicKey);
        } finally {
            if (material != null) {
                Arrays.fill(material, (byte) 0);
            }
        }
    }

    /** 언래핑 — 호출자가 반드시 finally 에서 zeroize 한다. 실패(GCM 태그 불일치)는 GeneralSecurityException. */
    public byte[] unwrap(String wrappedKey, String iv) throws GeneralSecurityException {
        try {
            return AesGcmSupport.decrypt(masterKeyHolder.getKey(),
                    Base64.getDecoder().decode(iv), Base64.getDecoder().decode(wrappedKey));
        } catch (RuntimeException e) {
            throw new GeneralSecurityException("키 재료 언래핑 실패", e);
        }
    }

    private Generated wrap(byte[] material, String publicKey) {
        byte[] iv = randomBytes(CryptoConstants.GCM_IV_LENGTH_BYTES);
        byte[] wrapped = AesGcmSupport.encrypt(masterKeyHolder.getKey(), iv, material);
        return new Generated(Base64.getEncoder().encodeToString(wrapped),
                Base64.getEncoder().encodeToString(iv), publicKey);
    }

    private byte[] randomBytes(int length) {
        byte[] out = new byte[length];
        random.nextBytes(out);
        return out;
    }

    private KeyPair generateKeyPair(KeyAlgorithm algorithm, int size) {
        try {
            if (algorithm == KeyAlgorithm.RSA) {
                KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                gen.initialize(size, random);
                return gen.generateKeyPair();
            }
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec(size == 384 ? "secp384r1" : "secp256r1"), random);
            return gen.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("키쌍 생성에 실패했습니다: " + algorithm, e);
        }
    }
}
