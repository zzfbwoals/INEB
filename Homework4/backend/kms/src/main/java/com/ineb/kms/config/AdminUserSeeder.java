package com.ineb.kms.config;

import com.ineb.kms.domain.AdminStatus;
import com.ineb.kms.domain.AdminUser;
import com.ineb.kms.domain.Role;
import com.ineb.kms.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 최초 기동 시 관리자 계정 시드. 회원가입 기능이 없으므로 이 계정으로만 로그인한다.
 * 초기 비밀번호는 환경변수 KMS_ADMIN_INIT_PASSWORD, 미설정 시 설계 문서의 문서화된 초기값.
 */
@Component
public class AdminUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private static final String INIT_PASSWORD_ENV = "KMS_ADMIN_INIT_PASSWORD";
    private static final String DOCUMENTED_DEFAULT_PASSWORD = "Admin!@#$5";

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
        String initPassword = environment.getProperty(INIT_PASSWORD_ENV, DOCUMENTED_DEFAULT_PASSWORD);
        adminUserRepository.save(new AdminUser(
                "admin",
                passwordEncoder.encode(initPassword),
                "관리자",
                Role.ADMIN,
                AdminStatus.ACTIVE));
        log.info("최초 기동: admin 관리자 계정을 시드했습니다");
    }
}
