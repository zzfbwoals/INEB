package com.ineb.kms.config;

import com.ineb.kms.crypto.ConfigSecretCodec;
import com.ineb.kms.crypto.MasterPassphrase;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.sql.DataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * DB 비밀번호는 application.yml에 ENC(...) 암호문으로 두고, 기동 시 마스터 패스프레이즈로 복호화해 DataSource에 주입한다.
 * crypto_config(salt)는 DB 안에 있어 DataSource 생성 전에는 읽을 수 없으므로, 마스터키가 아닌
 * 암호문 동봉 salt로 유도한 설정 전용 KEK를 쓴다 (ConfigSecretCodec 참조).
 * 패스프레이즈가 틀리면 여기서 먼저 GCM 태그 검증 실패로 기동이 중단된다(KCV 검증보다 앞 단계).
 */
@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource(Environment environment) {
        String url = environment.getRequiredProperty("spring.datasource.url");
        String username = environment.getRequiredProperty("spring.datasource.username");
        String password = environment.getRequiredProperty("spring.datasource.password");

        if (ConfigSecretCodec.isEncrypted(password)) {
            char[] passphrase = MasterPassphrase.load(environment);
            byte[] plain = null;
            try {
                plain = ConfigSecretCodec.decrypt(passphrase, password);
                password = new String(plain, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(passphrase, '\0');
                if (plain != null) {
                    Arrays.fill(plain, (byte) 0);
                }
            }
        }

        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .build();
    }
}
