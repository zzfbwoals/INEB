package com.ineb.kms.key;

import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import java.util.Base64;

/**
 * 테스트 암호문·서명값 문자열 형식. 버전을 접두에 내장해 복호화·검증 시 버전을 자동 판별한다.
 *   암호문: "{version}:{base64 iv}:{base64 ciphertext(+tag)}"  — IV 가 없는 모드(ECB·RSA)는 iv 자리가 빈 문자열
 *   서명값: "{version}:{base64 signature}"
 */
public final class CipherTextFormat {

    public record Cipher(int version, byte[] iv, byte[] cipherText) { }

    public record Signature(int version, byte[] signature) { }

    private CipherTextFormat() {
    }

    public static String encodeCipher(int version, byte[] iv, byte[] cipherText) {
        return version + ":" + (iv == null || iv.length == 0 ? "" : b64(iv)) + ":" + b64(cipherText);
    }

    public static String encodeSignature(int version, byte[] signature) {
        return version + ":" + b64(signature);
    }

    public static Cipher parseCipher(String text) {
        String[] parts = split(text, 3);
        byte[] iv = parts[1].isEmpty() ? new byte[0] : decode(parts[1]);
        return new Cipher(version(parts[0]), iv, decode(parts[2]));
    }

    public static Signature parseSignature(String text) {
        String[] parts = split(text, 2);
        return new Signature(version(parts[0]), decode(parts[1]));
    }

    private static String[] split(String text, int expected) {
        if (text == null) {
            throw new BusinessException(ErrorCode.KEY_CIPHERTEXT_FORMAT);
        }
        String[] parts = text.trim().split(":", -1);
        if (parts.length != expected || parts[parts.length - 1].isEmpty()) {
            throw new BusinessException(ErrorCode.KEY_CIPHERTEXT_FORMAT);
        }
        return parts;
    }

    private static int version(String s) {
        if (!s.matches("\\d{1,4}")) {
            throw new BusinessException(ErrorCode.KEY_CIPHERTEXT_FORMAT);
        }
        return Integer.parseInt(s);
    }

    private static byte[] decode(String s) {
        try {
            return Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.KEY_CIPHERTEXT_FORMAT);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
