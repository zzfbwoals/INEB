package com.ineb.kms.key;

import com.ineb.kms.common.BusinessException;
import com.ineb.kms.common.ErrorCode;
import com.ineb.kms.domain.CryptoKey;
import com.ineb.kms.domain.KeyAlgorithm;
import com.ineb.kms.domain.KeyMaterial;
import com.ineb.kms.domain.KeyState;
import com.ineb.kms.domain.KeyUsageLog;
import com.ineb.kms.domain.UsageOperation;
import com.ineb.kms.domain.UsageResult;
import com.ineb.kms.key.dto.CipherResponse;
import com.ineb.kms.key.dto.PlainResponse;
import com.ineb.kms.key.dto.SignResponse;
import com.ineb.kms.key.dto.VerifyResponse;
import com.ineb.kms.repository.KeyMaterialRepository;
import com.ineb.kms.repository.KeyUsageLogRepository;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 암복호화·서명검증 테스트 (KMS 관리 키 동작 검증). 공통 흐름:
 *  ① 키 조회·용도 검사 ② 암호화·서명은 current 버전(ACTIVE 만) / 복호화·검증은 접두 version 조회(ACTIVE 만)
 *  ③ 언래핑 직전 무결성 검증(위반 시 자동 정지·409) ④ 언래핑 → 연산 → 평문 재료 zeroize ⑤ key_usage_log 기록(성공·실패 모두)
 * 실패도 기록해야 하므로 BusinessException 은 롤백하지 않는다.
 */
@Service
public class KeyTestService {

    private final KeyService keyService;
    private final KeyMaterialRepository materialRepository;
    private final KeyUsageLogRepository usageLogRepository;
    private final KeyMaterialFactory materialFactory;
    private final KeyIntegrityGuard integrityGuard;
    private final AuditHook auditHook;

    public KeyTestService(KeyService keyService, KeyMaterialRepository materialRepository,
                          KeyUsageLogRepository usageLogRepository, KeyMaterialFactory materialFactory,
                          KeyIntegrityGuard integrityGuard, AuditHook auditHook) {
        this.keyService = keyService;
        this.materialRepository = materialRepository;
        this.usageLogRepository = usageLogRepository;
        this.materialFactory = materialFactory;
        this.integrityGuard = integrityGuard;
        this.auditHook = auditHook;
    }

    // ---------------------------------------------------------------- 암호화 · 복호화

    @Transactional(noRollbackFor = BusinessException.class)
    public CipherResponse encrypt(String keyUid, String plaintext, String actor) {
        CryptoKey key = keyService.load(keyUid);
        requirePurpose(key, key.getPurpose().canEncrypt());
        KeyMaterial m = currentUsable(key, UsageOperation.ENCRYPT);
        byte[] plain = plaintext.getBytes(StandardCharsets.UTF_8);
        int max = KeyCipherSupport.maxPlaintextBytes(key.getAlgorithm(), key.getKeySize());
        if (max > 0 && plain.length > max) {
            fail(key, m.getVersion(), UsageOperation.ENCRYPT, ErrorCode.KEY_PLAINTEXT_TOO_LONG);
        }
        String result = withMaterial(key, m, UsageOperation.ENCRYPT, material -> {
            if (key.getAlgorithm() == KeyAlgorithm.RSA) {
                return CipherTextFormat.encodeCipher(m.getVersion(), null, KeyCipherSupport.encryptRsa(m.getPublicKey(), plain));
            }
            KeyCipherSupport.Encrypted enc = KeyCipherSupport.encryptSymmetric(key.getAlgorithm(), key.getMode(), material, plain);
            return CipherTextFormat.encodeCipher(m.getVersion(), enc.iv(), enc.cipherText());
        });
        success(key, m.getVersion(), UsageOperation.ENCRYPT, actor, "KEY_TEST_ENCRYPT");
        return new CipherResponse(result, m.getVersion());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public PlainResponse decrypt(String keyUid, String ciphertext, String actor) {
        CryptoKey key = keyService.load(keyUid);
        requirePurpose(key, key.getPurpose().canEncrypt());
        CipherTextFormat.Cipher parsed = CipherTextFormat.parseCipher(ciphertext);
        KeyMaterial m = versionUsable(key, parsed.version(), UsageOperation.DECRYPT);
        byte[] plain = withMaterial(key, m, UsageOperation.DECRYPT, material ->
                key.getAlgorithm() == KeyAlgorithm.RSA
                        ? KeyCipherSupport.decryptRsa(material, parsed.cipherText())
                        : KeyCipherSupport.decryptSymmetric(key.getAlgorithm(), key.getMode(), material,
                                parsed.iv(), parsed.cipherText()));
        success(key, m.getVersion(), UsageOperation.DECRYPT, actor, "KEY_TEST_DECRYPT");
        return new PlainResponse(new String(plain, StandardCharsets.UTF_8), m.getVersion(),
                m.getVersion() != key.getCurrentVersion());
    }

    // ---------------------------------------------------------------- 서명 · 검증

    @Transactional(noRollbackFor = BusinessException.class)
    public SignResponse sign(String keyUid, String message, String actor) {
        CryptoKey key = keyService.load(keyUid);
        requirePurpose(key, key.getPurpose().canSign());
        KeyMaterial m = currentUsable(key, UsageOperation.SIGN);
        byte[] msg = message.getBytes(StandardCharsets.UTF_8);
        byte[] sig = withMaterial(key, m, UsageOperation.SIGN, material ->
                KeyCipherSupport.sign(key.getAlgorithm(), key.getKeySize(), material, msg));
        success(key, m.getVersion(), UsageOperation.SIGN, actor, "KEY_TEST_SIGN");
        return new SignResponse(CipherTextFormat.encodeSignature(m.getVersion(), sig), m.getVersion());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public VerifyResponse verify(String keyUid, String message, String signature, String actor) {
        CryptoKey key = keyService.load(keyUid);
        requirePurpose(key, key.getPurpose().canSign());
        CipherTextFormat.Signature parsed = CipherTextFormat.parseSignature(signature);
        KeyMaterial m = versionUsable(key, parsed.version(), UsageOperation.VERIFY);
        byte[] msg = message.getBytes(StandardCharsets.UTF_8);
        boolean valid;
        if (key.getAlgorithm().getKind() == KeyAlgorithm.Kind.HMAC) {
            valid = withMaterial(key, m, UsageOperation.VERIFY, material ->
                    KeyCipherSupport.verify(key.getAlgorithm(), key.getKeySize(), material, null, msg, parsed.signature()));
        } else {
            // 비대칭 검증은 공개키만 필요 — 언래핑 없이 수행하되 무결성 검증은 동일하게 적용
            integrityGuard.verifyOrDeactivate(m);
            valid = KeyCipherSupport.verify(key.getAlgorithm(), key.getKeySize(), null, m.getPublicKey(), msg, parsed.signature());
        }
        usageLogRepository.save(new KeyUsageLog(key, m.getVersion(), UsageOperation.VERIFY,
                valid ? UsageResult.SUCCESS : UsageResult.FAIL, valid ? null : "서명 불일치"));
        auditHook.record(actor, "KEY_TEST_VERIFY", key.getKeyUid(), "version=" + m.getVersion() + ", valid=" + valid);
        return new VerifyResponse(valid, m.getVersion(), m.getVersion() != key.getCurrentVersion());
    }

    // ---------------------------------------------------------------- 공통

    private interface MaterialFunction<T> {
        T apply(byte[] material);
    }

    /** 무결성 검증 → 언래핑 → 연산 → zeroize. 실패는 usage_log 에 남기고 예외를 그대로 던진다. */
    private <T> T withMaterial(CryptoKey key, KeyMaterial m, UsageOperation op, MaterialFunction<T> fn) {
        try {
            integrityGuard.verifyOrDeactivate(m);
        } catch (BusinessException e) {
            record(key, m.getVersion(), op, UsageResult.FAIL, e.getErrorCode().getMessage());
            throw e;
        }
        byte[] material = null;
        try {
            material = materialFactory.unwrap(m.getWrappedKey(), m.getIv());
            return fn.apply(material);
        } catch (GeneralSecurityException e) {
            fail(key, m.getVersion(), op, ErrorCode.KEY_MATERIAL_CORRUPTED);
            return null;   // unreachable
        } catch (BusinessException e) {
            record(key, m.getVersion(), op, UsageResult.FAIL, e.getErrorCode().getMessage());
            throw e;
        } finally {
            if (material != null) {
                Arrays.fill(material, (byte) 0);
            }
        }
    }

    private void requirePurpose(CryptoKey key, boolean allowed) {
        if (!allowed) {
            throw new BusinessException(ErrorCode.KEY_PURPOSE_MISMATCH);
        }
    }

    /** 암호화·서명: current 버전이 ACTIVE 일 때만 */
    private KeyMaterial currentUsable(CryptoKey key, UsageOperation op) {
        KeyMaterial m = materialRepository.findByKeyIdAndVersion(key.getId(), key.getCurrentVersion())
                .orElseThrow(() -> new BusinessException(ErrorCode.KEY_VERSION_NOT_FOUND));
        if (m.getState() != KeyState.ACTIVE) {
            fail(key, m.getVersion(), op, ErrorCode.KEY_VERSION_NOT_USABLE);
        }
        return m;
    }

    /** 복호화·검증: 접두 version 이 ACTIVE 일 때만 (구 버전 허용, DEACTIVATED·PRE_ACTIVE·DESTROYED 차단) */
    private KeyMaterial versionUsable(CryptoKey key, int version, UsageOperation op) {
        KeyMaterial m = materialRepository.findByKeyIdAndVersion(key.getId(), version)
                .orElseThrow(() -> {
                    record(key, version, op, UsageResult.FAIL, ErrorCode.KEY_VERSION_NOT_FOUND.getMessage());
                    return new BusinessException(ErrorCode.KEY_VERSION_NOT_FOUND);
                });
        if (m.getState() != KeyState.ACTIVE) {
            fail(key, m.getVersion(), op, ErrorCode.KEY_VERSION_NOT_USABLE);
        }
        return m;
    }

    private void fail(CryptoKey key, int version, UsageOperation op, ErrorCode code) {
        record(key, version, op, UsageResult.FAIL, code.getMessage());
        throw new BusinessException(code);
    }

    private void success(CryptoKey key, int version, UsageOperation op, String actor, String auditAction) {
        record(key, version, op, UsageResult.SUCCESS, null);
        auditHook.record(actor, auditAction, key.getKeyUid(),
                "version=" + version + (version != key.getCurrentVersion() ? ", oldVersion=true" : ""));
    }

    private void record(CryptoKey key, int version, UsageOperation op, UsageResult result, String failReason) {
        usageLogRepository.save(new KeyUsageLog(key, version, op, result,
                failReason == null ? null : failReason.substring(0, Math.min(failReason.length(), 200))));
    }
}
