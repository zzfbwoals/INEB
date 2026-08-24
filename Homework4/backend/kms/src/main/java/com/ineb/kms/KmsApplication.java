package com.ineb.kms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * 인증은 JWT 필터 + AuthService(DB 조회)로만 처리한다.
 * UserDetailsServiceAutoConfiguration을 제외해 쓰이지 않는 인메모리 기본 사용자(및 기동 시 랜덤 비밀번호 로그)를 만들지 않는다.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class KmsApplication {

	public static void main(String[] args) {
		SpringApplication.run(KmsApplication.class, args);
	}

}
