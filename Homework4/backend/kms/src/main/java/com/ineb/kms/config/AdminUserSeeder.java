package com.ineb.kms.config;

import com.ineb.kms.crypto.ConfigSecretCodec;
import com.ineb.kms.crypto.MasterKeyException;
import com.ineb.kms.crypto.MasterPassphrase;
import com.ineb.kms.domain.AdminStatus;
import com.ineb.kms.domain.AdminUser;
import com.ineb.kms.domain.Role;
import com.ineb.kms.repository.AdminUserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 최초 기동 시 관리자 계정 시드. 회원가입 기능이 없으므로 이 계정으로만 로그인한다.
 * 초기 비밀번호는 application.yml의 kms.admin.init-password에 ENC(...) 암호문으로 두고
 * (DB 비밀번호와 같은 방식), admin_user가 비어 있을 때만 마스터 패스프레이즈로 복호화해 BCrypt 해시로 저장한다.
 * 평문은 코드·저장소·환경변수 어디에도 남지 않는다.
 */
@Component
public class AdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    static final String INIT_PASSWORD_PROPERTY = "kms.admin.init-password";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public AdminUserSeeder(AdminUserRepository adminUserRepository,
                           PasswordEncoder passwordEncoder,
                           Environment environment) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminUserRepository.count() > 0) {
            return;
        }
        String passwordHash = hashInitPassword();
        adminUserRepository.save(new AdminUser(
                "admin",
                passwordHash,
                "관리자",
                Role.ADMIN,
                AdminStatus.ACTIVE));
        log.info("최초 기동: admin 관리자 계정을 시드했습니다");
    }

    private String hashInitPassword() {
        String encoded = environment.getProperty(INIT_PASSWORD_PROPERTY);
        if (!ConfigSecretCodec.isEncrypted(encoded)) {
            throw new MasterKeyException(
                    INIT_PASSWORD_PROPERTY + "는 ENC(...) 암호문이어야 합니다 (평문 금지) — 기동을 중단합니다");
        }
        char[] passphrase = MasterPassphrase.load(environment);
        byte[] plain = null;
        try {
            plain = ConfigSecretCodec.decrypt(passphrase, encoded);
            return passwordEncoder.encode(new String(plain, StandardCharsets.UTF_8));
        } finally {
            Arrays.fill(passphrase, '\0');
            if (plain != null) {
                Arrays.fill(plain, (byte) 0);
            }
        }
    }
}
