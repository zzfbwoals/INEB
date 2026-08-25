package com.ineb.kms.tool;

import com.ineb.kms.crypto.ConfigSecretCodec;
import com.ineb.kms.crypto.CryptoConstants;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * application.yml에 넣을 ENC(...) 암호문 생성 도구 (Spring 컨텍스트 없이 동작).
 * <pre>
 *   $env:KMS_MASTER_PASSPHRASE = "..."          # 운영이면 kms.env의 값과 동일해야 함
 *   "평문비밀번호" | ./gradlew.bat -q encryptSecret
 * </pre>
 * 평문은 셸 히스토리·ps 노출을 피하기 위해 인자가 아닌 표준입력으로만 받는다.
 */
public final class ConfigSecretEncryptTool {

    private ConfigSecretEncryptTool() {
    }

    public static void main(String[] args) throws IOException {
        String passphraseValue = System.getenv(CryptoConstants.MASTER_PASSPHRASE_ENV);
        if (passphraseValue == null || passphraseValue.isBlank()) {
            System.err.println("환경변수 " + CryptoConstants.MASTER_PASSPHRASE_ENV + "가 필요합니다");
            System.exit(1);
            return;
        }

        String line;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            line = reader.readLine();
        }
        if (line == null || line.isEmpty()) {
            System.err.println("표준입력으로 평문을 넣어 주세요. 예) \"secret\" | ./gradlew.bat -q encryptSecret");
            System.exit(1);
            return;
        }

        char[] passphrase = passphraseValue.toCharArray();
        byte[] plain = line.getBytes(StandardCharsets.UTF_8);
        try {
            System.out.println(ConfigSecretCodec.encrypt(passphrase, plain));
        } finally {
            Arrays.fill(passphrase, '\0');
            Arrays.fill(plain, (byte) 0);
        }
    }
}
