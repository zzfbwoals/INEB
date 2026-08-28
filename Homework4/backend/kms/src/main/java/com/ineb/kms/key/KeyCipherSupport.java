package com.ineb.kms.key;

import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.domain.KeyAlgorithm;
import com.ineb.kms.domain.KeyMode;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.LEAEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.modes.SICBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

/**
 * 알고리즘·모드별 JCE 연산 (구현설계 1-9). 재료는 byte[] 로만 받고, 여기서는 복사·보관하지 않는다.
 * <pre>
 *  AES·ARIA·LEA·SEED  CBC/PKCS5(iv16) · GCM/NoPadding(iv12, tag128) · CTR/NoPadding(iv16) · ECB/PKCS5(iv 없음, 비권장)
 *                     — LEA 는 BC 에 JCE 등록이 없어 경량 API(LEAEngine) 로 동일 모드를 구성한다
 *  RSA                RSA/ECB/OAEPWithSHA-256AndMGF1Padding (평문 상한 = 키바이트 − 66) · SHA256withRSA
 *  ECDSA              SHA256withECDSA (P-256) / SHA384withECDSA (P-384)
 *  HMAC               HmacSHA256 / HmacSHA512 — verify 는 재계산 후 상수 시간 비교
 * </pre>
 */
public final class KeyCipherSupport {

    public record Encrypted(byte[] iv, byte[] cipherText) { }

    private static final int GCM_IV = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int BLOCK_IV = 16;
    private static final int OAEP_SHA256_OVERHEAD = 66;
    private static final SecureRandom RANDOM = new SecureRandom();

    private KeyCipherSupport() {
    }

    /** RSA-OAEP(SHA-256) 평문 상한. 대칭·HMAC 은 제한 없음(-1). */
    public static int maxPlaintextBytes(KeyAlgorithm algorithm, int keySize) {
        return algorithm == KeyAlgorithm.RSA ? keySize / 8 - OAEP_SHA256_OVERHEAD : -1;
    }

    // ---------------------------------------------------------------- 대칭

    public static Encrypted encryptSymmetric(KeyAlgorithm alg, KeyMode mode, byte[] key, byte[] plain) {
        try {
            byte[] iv = mode == KeyMode.ECB ? new byte[0] : random(mode == KeyMode.GCM ? GCM_IV : BLOCK_IV);
            if (alg == KeyAlgorithm.LEA) {
                return new Encrypted(iv, lea(mode, key, iv, plain, true));
            }
            Cipher cipher = symmetricCipher(alg, mode, key, iv, Cipher.ENCRYPT_MODE);
            return new Encrypted(iv, cipher.doFinal(plain));
        } catch (GeneralSecurityException | InvalidCipherTextException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.KEY_CRYPTO_FAILED);
        }
    }

    public static byte[] decryptSymmetric(KeyAlgorithm alg, KeyMode mode, byte[] key, byte[] iv, byte[] cipherText) {
        try {
            if (alg == KeyAlgorithm.LEA) {
                return lea(mode, key, iv, cipherText, false);
            }
            return symmetricCipher(alg, mode, key, iv, Cipher.DECRYPT_MODE).doFinal(cipherText);
        } catch (GeneralSecurityException | InvalidCipherTextException | IllegalArgumentException
                 | IllegalStateException e) {
            throw new BusinessException(ErrorCode.KEY_CRYPTO_FAILED);
        }
    }

    /** LEA — Bouncy Castle 경량 API. 블록 16바이트, 키 128/192/256. */
    private static byte[] lea(KeyMode mode, byte[] key, byte[] iv, byte[] input, boolean encrypt)
            throws InvalidCipherTextException {
        BlockCipher engine = new LEAEngine();
        KeyParameter kp = new KeyParameter(key);
        switch (mode) {
            case GCM -> {
                var gcm = GCMBlockCipher.newInstance(engine);
                gcm.init(encrypt, new AEADParameters(kp, GCM_TAG_BITS, iv));
                byte[] out = new byte[gcm.getOutputSize(input.length)];
                int n = gcm.processBytes(input, 0, input.length, out, 0);
                n += gcm.doFinal(out, n);
                return trim(out, n);
            }
            case CTR -> {
                var ctr = SICBlockCipher.newInstance(engine);
                ctr.init(encrypt, new ParametersWithIV(kp, iv));
                byte[] out = new byte[input.length];
                ctr.processBytes(input, 0, input.length, out, 0);
                return out;
            }
            case CBC, ECB -> {
                PaddedBufferedBlockCipher c = new PaddedBufferedBlockCipher(
                        mode == KeyMode.CBC ? CBCBlockCipher.newInstance(engine) : engine);
                c.init(encrypt, mode == KeyMode.CBC ? new ParametersWithIV(kp, iv) : kp);
                byte[] out = new byte[c.getOutputSize(input.length)];
                int n = c.processBytes(input, 0, input.length, out, 0);
                n += c.doFinal(out, n);
                return trim(out, n);
            }
        }
        throw new IllegalArgumentException("unsupported mode " + mode);
    }

    private static byte[] trim(byte[] buf, int len) {
        return len == buf.length ? buf : java.util.Arrays.copyOf(buf, len);
    }

    private static Cipher symmetricCipher(KeyAlgorithm alg, KeyMode mode, byte[] key, byte[] iv, int opmode)
            throws GeneralSecurityException {
        String transformation = alg.name() + "/" + mode.name() + "/" + switch (mode) {
            case GCM, CTR -> "NoPadding";
            case CBC, ECB -> "PKCS5Padding";
        };
        Cipher cipher = Cipher.getInstance(transformation);
        SecretKeySpec spec = new SecretKeySpec(key, alg.name());
        switch (mode) {
            case ECB -> cipher.init(opmode, spec);
            case GCM -> cipher.init(opmode, spec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            default -> cipher.init(opmode, spec, new IvParameterSpec(iv));
        }
        return cipher;
    }

    // ---------------------------------------------------------------- RSA 암복호화

    public static byte[] encryptRsa(String publicKeyBase64, byte[] plain) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey("RSA", publicKeyBase64));
            return cipher.doFinal(plain);
        } catch (GeneralSecurityException e) {
            throw new BusinessException(ErrorCode.KEY_CRYPTO_FAILED);
        }
    }

    public static byte[] decryptRsa(byte[] privateKeyPkcs8, byte[] cipherText) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey("RSA", privateKeyPkcs8));
            return cipher.doFinal(cipherText);
        } catch (GeneralSecurityException e) {
            throw new BusinessException(ErrorCode.KEY_CRYPTO_FAILED);
        }
    }

    // ---------------------------------------------------------------- 서명 · 검증

    public static byte[] sign(KeyAlgorithm alg, int keySize, byte[] material, byte[] message) {
        try {
            if (alg.getKind() == KeyAlgorithm.Kind.HMAC) {
                return hmac(alg, material, message);
            }
            Signature sig = Signature.getInstance(signatureAlgorithm(alg, keySize));
            sig.initSign(privateKey(jcaName(alg), material));
            sig.update(message);
            return sig.sign();
        } catch (GeneralSecurityException e) {
            throw new BusinessException(ErrorCode.KEY_CRYPTO_FAILED);
        }
    }

    /**
     * @param material HMAC 이면 비밀키, 비대칭이면 사용하지 않음(null 허용)
     * @param publicKeyBase64 비대칭이면 X.509 공개키
     */
    public static boolean verify(KeyAlgorithm alg, int keySize, byte[] material, String publicKeyBase64,
                                 byte[] message, byte[] signature) {
        try {
            if (alg.getKind() == KeyAlgorithm.Kind.HMAC) {
                return MessageDigest.isEqual(hmac(alg, material, message), signature);
            }
            Signature sig = Signature.getInstance(signatureAlgorithm(alg, keySize));
            sig.initVerify(publicKey(jcaName(alg), publicKeyBase64));
            sig.update(message);
            return sig.verify(signature);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;   // 형식이 깨진 서명값은 "검증 실패"로 취급
        }
    }

    private static byte[] hmac(KeyAlgorithm alg, byte[] key, byte[] message) throws GeneralSecurityException {
        String name = alg == KeyAlgorithm.SHA512 ? "HmacSHA512" : "HmacSHA256";
        Mac mac = Mac.getInstance(name);
        mac.init(new SecretKeySpec(key, name));
        return mac.doFinal(message);
    }

    private static String signatureAlgorithm(KeyAlgorithm alg, int keySize) {
        if (alg == KeyAlgorithm.RSA) {
            return "SHA256withRSA";
        }
        return keySize == 384 ? "SHA384withECDSA" : "SHA256withECDSA";
    }

    private static String jcaName(KeyAlgorithm alg) {
        return alg == KeyAlgorithm.RSA ? "RSA" : "EC";
    }

    private static PrivateKey privateKey(String jca, byte[] pkcs8) throws GeneralSecurityException {
        return KeyFactory.getInstance(jca).generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    private static PublicKey publicKey(String jca, String base64) throws GeneralSecurityException {
        return KeyFactory.getInstance(jca).generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    private static byte[] random(int length) {
        byte[] out = new byte[length];
        RANDOM.nextBytes(out);
        return out;
    }
}
