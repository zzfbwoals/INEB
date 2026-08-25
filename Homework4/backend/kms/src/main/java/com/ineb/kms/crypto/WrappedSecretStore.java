package com.ineb.kms.crypto;

import com.ineb.kms.domain.CryptoConfig;
import com.ineb.kms.repository.CryptoConfigRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 내부 비밀키(JWT 서명 키·무결성 HMAC 키) 보관소.
 * <p>
 * 환경변수로 받지 않고 최초 기동 시 SecureRandom으로 생성해 마스터키로 래핑(AES-256-GCM)하여
 * crypto_config에 base64(iv|wrapped+tag)로 저장한다 — KMS 관리 키(key_material)와 동일한 봉투 암호화 패턴.
 * 이후 기동에서는 읽어서 언래핑만 한다. 값은 byte[]로만 보관하고 종료 시 zeroize한다.
 * 마스터키와 별개의 랜덤 값이므로 패스프레이즈를 바꿔도(재래핑만 하면) 키 자체는 유지된다.
 */
@Component
public class WrappedSecretStore {

    private static final Logger log = LoggerFactory.getLogger(WrappedSecretStore.class);

    private static final int SECRET_LENGTH_BYTES = 32;
    private static final int IV_LEN = CryptoConstants.GCM_IV_LENGTH_BYTES;

    private final CryptoConfigRepository cryptoConfigRepository;
    private final MasterKeyHolder masterKeyHolder;

    private byte[] jwtKey;
    private byte[] integrityKey;

    /** MasterKeyInitializer를 주입받아 마스터키 유도·KCV 검증 이후에 초기화되도록 순서를 강제한다. */
    public WrappedSecretStore(CryptoConfigRepository cryptoConfigRepository,
                              MasterKeyHolder masterKeyHolder,
                              MasterKeyInitializer masterKeyInitializer) {
        this.cryptoConfigRepository = cryptoConfigRepository;
        this.masterKeyHolder = masterKeyHolder;
    }

    @PostConstruct
    public void initialize() {
        jwtKey = loadOrCreate(CryptoConstants.CONFIG_KEY_JWT_KEY);
        integrityKey = loadOrCreate(CryptoConstants.CONFIG_KEY_INTEGRITY_KEY);
        log.info("내부 비밀키(JWT·무결성) 언래핑 완료");
    }

    /** HS256 JWT 서명 키 (32바이트). 반환 배열을 변경하거나 String으로 바꾸지 말 것. */
    public byte[] jwtKey() {
        return require(jwtKey, "JWT");
    }

    /** 행 무결성 HMAC-SHA256 키 (32바이트). */
    public byte[] integrityKey() {
        return require(integrityKey, "무결성");
    }

    private byte[] loadOrCreate(String configKey) {
        byte[] masterKey = masterKeyHolder.getKey();
        Optional<CryptoConfig> stored = cryptoConfigRepository.findById(configKey);
        if (stored.isPresent()) {
            return unwrap(masterKey, stored.get().getConfigValue(), configKey);
        }

        byte[] secret = new byte[SECRET_LENGTH_BYTES];
        new SecureRandom().nextBytes(secret);
        cryptoConfigRepository.save(new CryptoConfig(configKey, wrap(masterKey, secret)));
        log.info("최초 기동: {} 키를 생성하여 마스터키로 래핑 후 crypto_config에 저장했습니다", configKey);
        return secret;
    }

    private static String wrap(byte[] masterKey, byte[] secret) {
        byte[] iv = new byte[IV_LEN];
        new SecureRandom().nextBytes(iv);
        byte[] wrapped = AesGcmSupport.encrypt(masterKey, iv, secret);
        byte[] blob = new byte[IV_LEN + wrapped.length];
        System.arraycopy(iv, 0, blob, 0, IV_LEN);
        System.arraycopy(wrapped, 0, blob, IV_LEN, wrapped.length);
        return Base64.getEncoder().encodeToString(blob);
    }

    private static byte[] unwrap(byte[] masterKey, String encoded, String configKey) {
        byte[] blob = Base64.getDecoder().decode(encoded);
        byte[] iv = Arrays.copyOfRange(blob, 0, IV_LEN);
        byte[] wrapped = Arrays.copyOfRange(blob, IV_LEN, blob.length);
        try {
            return AesGcmSupport.decrypt(masterKey, iv, wrapped);
        } catch (MasterKeyException e) {
            throw new MasterKeyException("crypto_config." + configKey + " 언래핑 실패 — 값이 손상되었습니다", e);
        }
    }

    private static byte[] require(byte[] value, String label) {
        if (value == null) {
            throw new MasterKeyException(label + " 키가 초기화되지 않았습니다");
        }
        return value;
    }

    @PreDestroy
    public void destroy() {
        if (jwtKey != null) {
            Arrays.fill(jwtKey, (byte) 0);
            jwtKey = null;
        }
        if (integrityKey != null) {
            Arrays.fill(integrityKey, (byte) 0);
            integrityKey = null;
        }
    }
}
