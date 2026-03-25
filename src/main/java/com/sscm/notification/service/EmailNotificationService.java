package com.sscm.notification.service;

import com.sscm.notification.event.NotificationEvent;
import com.sscm.auth.entity.User;
import com.sscm.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    @Value("${spring.mail.username:noreply@sscm.dev}")
    private String fromAddress;

    @Async
    public void sendEmailNotification(NotificationEvent event) {
        for (Long recipientId : event.getRecipientIds()) {
            try {
                User recipient = userRepository.findById(recipientId).orElse(null);
                if (recipient == null || recipient.getEmail() == null) {
                    continue;
                }

                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress);
                message.setTo(recipient.getEmail());
                message.setSubject("[SSCM] " + event.getTitle());
                message.setText(event.getMessage());
                mailSender.send(message);

                log.debug("이메일 발송 완료: to={}, subject={}", recipient.getEmail(), event.getTitle());
            } catch (Exception e) {
                log.warn("이메일 발송 실패: recipientId={}, error={}", recipientId, e.getMessage());
            }
        }
    }
}
