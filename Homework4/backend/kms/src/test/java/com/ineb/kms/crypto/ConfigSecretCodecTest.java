package com.ineb.kms.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfigSecretCodecTest {

    private static final char[] PASSPHRASE = "correct-master-passphrase-over-20".toCharArray();
    private static final byte[] PLAIN = "ineb!1234".getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("ENC(...) 형식으로 암호화되고 같은 패스프레이즈로 원문이 복원된다")
    void encryptDecryptRoundTrip() {
        String enc = ConfigSecretCodec.encrypt(PASSPHRASE, PLAIN);

        assertTrue(ConfigSecretCodec.isEncrypted(enc));
        assertArrayEquals(PLAIN, ConfigSecretCodec.decrypt(PASSPHRASE, enc));
    }

    @Test
    @DisplayName("같은 평문을 두 번 암호화하면 salt·iv가 달라 암호문이 다르지만 둘 다 복호화된다")
    void randomSaltAndIvPerEncryption() {
        String first = ConfigSecretCodec.encrypt(PASSPHRASE, PLAIN);
        String second = ConfigSecretCodec.encrypt(PASSPHRASE, PLAIN);

        assertNotEquals(first, second);
        assertArrayEquals(PLAIN, ConfigSecretCodec.decrypt(PASSPHRASE, first));
        assertArrayEquals(PLAIN, ConfigSecretCodec.decrypt(PASSPHRASE, second));
    }

    @Test
    @DisplayName("패스프레이즈가 한 글자만 틀려도 GCM 태그 불일치로 복호화가 실패한다(fail-fast)")
    void wrongPassphraseFails() {
        String enc = ConfigSecretCodec.encrypt(PASSPHRASE, PLAIN);
        char[] wrong = "correct-master-passphrase-over-2O".toCharArray();

        assertThrows(MasterKeyException.class, () -> ConfigSecretCodec.decrypt(wrong, enc));
    }

    @Test
    @DisplayName("암호문이 한 글자라도 변조되면 복호화가 실패한다")
    void tamperedCipherTextFails() {
        String enc = ConfigSecretCodec.encrypt(PASSPHRASE, PLAIN);
        int idx = enc.length() - 6;
        char replaced = enc.charAt(idx) == 'A' ? 'B' : 'A';
        String tampered = enc.substring(0, idx) + replaced + enc.substring(idx + 1);

        assertThrows(MasterKeyException.class, () -> ConfigSecretCodec.decrypt(PASSPHRASE, tampered));
    }

    @Test
    @DisplayName("ENC( 접두어가 없는 평문 값은 암호문으로 취급하지 않는다")
    void plainValueIsNotEncrypted() {
        assertFalse(ConfigSecretCodec.isEncrypted("ineb!1234"));
        assertFalse(ConfigSecretCodec.isEncrypted(null));
        assertThrows(MasterKeyException.class, () -> ConfigSecretCodec.decrypt(PASSPHRASE, "ineb!1234"));
    }
}
