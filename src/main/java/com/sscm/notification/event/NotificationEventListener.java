package com.sscm.notification.event;

import com.sscm.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async
    @EventListener
    public void handleNotificationEvent(NotificationEvent event) {
        log.debug("알림 이벤트 수신: type={}, recipients={}", event.getType(), event.getRecipientIds());
        try {
            notificationService.createNotifications(event);
        } catch (Exception e) {
            log.error("알림 생성 실패: type={}, error={}", event.getType(), e.getMessage(), e);
        }
    }
}
