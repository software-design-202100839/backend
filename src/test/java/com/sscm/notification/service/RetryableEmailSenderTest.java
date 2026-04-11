package com.sscm.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.sscm.common.config.RetryConfig;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SSCM-58 — spring-retry가 실제로 AOP 프록시를 통해 동작하는지 검증.
 * 단순 단위 테스트로는 @Retryable이 안 걸리므로 SpringBootTest로 컨텍스트 로드.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {RetryConfig.class, RetryableEmailSender.class})
@Import(RetryableEmailSender.class)
@DisplayName("RetryableEmailSender 재시도 동작 테스트")
class RetryableEmailSenderTest {

    @Autowired
    private RetryableEmailSender sender;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    @DisplayName("MailException 발생 시 최대 3회 재시도 후 @Recover로 전달된다")
    void retryThreeTimesThenRecover() {
        doThrow(new MailSendException("SMTP 일시 장애")).when(mailSender).send(any(SimpleMailMessage.class));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo("user@example.com");
        message.setSubject("test");
        message.setText("body");

        // @Recover가 정상 호출되므로 예외 전파되지 않음
        assertThatCode(() -> sender.send(message)).doesNotThrowAnyException();

        // 3회 시도 검증
        verify(mailSender, times(3)).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("정상 발송 시 1회만 호출되고 재시도 없음")
    void successfulSendNoRetry() {
        doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo("user@example.com");
        message.setSubject("test");
        message.setText("body");

        sender.send(message);

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }
}
