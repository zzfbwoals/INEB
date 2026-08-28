package com.ineb.kms.config;

import jakarta.annotation.PostConstruct;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.context.annotation.Configuration;

/** ARIA · SEED · LEA 등 JDK 미지원 알고리즘을 위해 Bouncy Castle 프로바이더를 기동 시 1회 등록한다. */
@Configuration
public class BouncyCastleConfig {

    @PostConstruct
    public void register() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
