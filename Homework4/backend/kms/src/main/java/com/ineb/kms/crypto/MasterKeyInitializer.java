package com.ineb.kms.crypto;

import com.ineb.kms.domain.CryptoConfig;
import com.ineb.kms.repository.CryptoConfigRepository;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 기동 시 1회: 마스터 패스프레이즈 → PBKDF2 마스터키 유도 → KCV 검증.
 * 검증 실패 시 예외를 던져 Spring 컨텍스트 기동을 중단한다(fail-fast).
 * "경고만 남기고 진행"은 틀린 키로 신규 데이터가 암호화되는 사고로 이어지므로 허용하지 않는다.
 */
@Component
public class MasterKeyInitializer {

    private static final Logger log = LoggerFactory.getLogger(MasterKeyInitializer.class);

    private final Environment environment;
    private final CryptoConfigRepository cryptoConfigRepository;
    private final MasterKeyHolder masterKeyHolder;

    public MasterKeyInitializer(Environment environment,
                                CryptoConfigRepository cryptoConfigRepository,
                                MasterKeyHolder masterKeyHolder) {
        this.environment = environment;
        this.cryptoConfigRepository = cryptoConfigRepository;
        this.masterKeyHolder = masterKeyHolder;
    }

    @PostConstruct
    public void initialize() {
        char[] passphrase = MasterPassphrase.load(environment);
        byte[] masterKey;
        try {
            byte[] salt = loadOrCreateSalt();
            masterKey = Pbkdf2Support.deriveKey(
                    passphrase, salt,
                    CryptoConstants.PBKDF2_ITERATIONS,
                    CryptoConstants.MASTER_KEY_LENGTH_BYTES);
        } finally {
            Arrays.fill(passphrase, '\0');
        }

        verifyOrCreateKcv(masterKey);
        masterKeyHolder.init(masterKey);
        log.info("마스터키 유도 및 KCV 검증 완료");
    }


    private byte[] loadOrCreateSalt() {
        Optional<CryptoConfig> stored = cryptoConfigRepository.findById(CryptoConstants.CONFIG_KEY_SALT);
        if (stored.isPresent()) {
            return Base64.getDecoder().decode(stored.get().getConfigValue());
        }
        byte[] salt = new byte[CryptoConstants.SALT_LENGTH_BYTES];
        new SecureRandom().nextBytes(salt);
        cryptoConfigRepository.save(new CryptoConfig(
                CryptoConstants.CONFIG_KEY_SALT, Base64.getEncoder().encodeToString(salt)));
        log.info("최초 기동: 마스터키 salt를 생성하여 crypto_config에 저장했습니다");
        return salt;
    }

    private void verifyOrCreateKcv(byte[] masterKey) {
        byte[] actual = AesGcmSupport.encrypt(
                masterKey,
                CryptoConstants.kcvFixedIv(),
                CryptoConstants.KCV_PLAIN_TEXT.getBytes(StandardCharsets.UTF_8));

        Optional<CryptoConfig> stored = cryptoConfigRepository.findById(CryptoConstants.CONFIG_KEY_KCV);
        if (stored.isEmpty()) {
            cryptoConfigRepository.save(new CryptoConfig(
                    CryptoConstants.CONFIG_KEY_KCV, Base64.getEncoder().encodeToString(actual)));
            log.info("최초 기동: KCV를 생성하여 crypto_config에 저장했습니다");
            return;
        }

        byte[] expected = Base64.getDecoder().decode(stored.get().getConfigValue());
        if (!MessageDigest.isEqual(expected, actual)) {
            Arrays.fill(masterKey, (byte) 0);
            throw new MasterKeyException(
                    "KCV 검증 실패 — 마스터 패스프레이즈가 올바르지 않습니다. 기동을 중단합니다");
        }
    }
}
