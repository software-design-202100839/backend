package com.sscm.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * SSCM-58: 이메일 발송 일시 장애(SMTP 네트워크 오류, 레이트 리밋 등) 자동 재시도.
 * 별도 빈으로 분리한 이유: spring-retry는 AOP 프록시 기반이라 같은 빈 내부 호출엔
 * 적용되지 않음. EmailNotificationService에서 이 빈을 주입받아 호출해야 재시도 동작.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetryableEmailSender {

    private final JavaMailSender mailSender;

    @Retryable(
            retryFor = MailException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void send(SimpleMailMessage message) {
        if (log.isDebugEnabled()) {
            String to = (message.getTo() != null && message.getTo().length > 0) ? message.getTo()[0] : "?";
            log.debug("이메일 발송 시도: to={}, subject={}", to, message.getSubject());
        }
        mailSender.send(message);
    }

    @Recover
    public void recover(MailException e, SimpleMailMessage message) {
        String to = (message.getTo() != null && message.getTo().length > 0) ? message.getTo()[0] : "?";
        log.warn("이메일 발송 최종 실패 (3회 재시도 후 포기): to={}, error={}", to, e.getMessage());
    }
}
