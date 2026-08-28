package com.ineb.kms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 키 생명주기 스케줄러(활성일·갱신 주기·무결성 배치) 활성화. 단일 인스턴스 전제이므로 분산 락 없음. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
