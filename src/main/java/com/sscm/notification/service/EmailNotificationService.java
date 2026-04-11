package com.sscm.notification.service;

import com.sscm.notification.event.NotificationEvent;
import com.sscm.auth.entity.User;
import com.sscm.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final RetryableEmailSender retryableEmailSender;
    private final UserRepository userRepository;

    @Value("${spring.mail.username:noreply@sscm.dev}")
    private String fromAddress;

    @Async
    public void sendEmailNotification(NotificationEvent event) {
        for (Long recipientId : event.getRecipientIds()) {
            User recipient = userRepository.findById(recipientId).orElse(null);
            if (recipient == null || recipient.getEmail() == null) {
                continue;
            }

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(recipient.getEmail());
            message.setSubject("[SSCM] " + event.getTitle());
            message.setText(event.getMessage());

            // SSCM-58: RetryableEmailSender가 MailException 발생 시 최대 3회 재시도.
            // 모두 실패하면 @Recover에서 로그만 남기고 정상 종료(다른 수신자 발송 계속).
            retryableEmailSender.send(message);
        }
    }
}
