package com.ineb.kms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // REST API 환경인 경우 CSRF 비활성화
                .csrf(AbstractHttpConfigurer::disable)

                // URL별 접근 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // 1. Swagger / OpenAPI 관련 엔드포인트 전체 허용
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()

                        // 2. 인증 없이 접근해야 하는 공용 API (예: 로그인, 회원가입 등)
                        .requestMatchers("/api/auth/**").permitAll()

                        // 3. 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}