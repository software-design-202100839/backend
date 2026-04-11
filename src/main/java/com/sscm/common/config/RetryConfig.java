package com.sscm.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * spring-retry 활성화. SSCM-58: 이메일 발송 일시 장애 시 자동 재시도 + (향후) 낙관적 락 충돌 재시도.
 */
@Configuration
@EnableRetry
public class RetryConfig {
}
